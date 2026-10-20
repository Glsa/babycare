// babycare-sync 云同步服务入口：配置装配 + 启停。
package main

import (
	"context"
	"errors"
	"flag"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"babycare/backend/internal/api"
	"babycare/backend/internal/store"
)

// envOr 环境变量优先级低于命令行 flag：flag 默认值先取环境变量。
func envOr(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func main() {
	addr := flag.String("addr", envOr("BABYCARE_ADDR", ":8080"), "监听地址")
	dbPath := flag.String("db", envOr("BABYCARE_DB", "babycare.db"), "SQLite 数据库文件路径")
	codeTTL := flag.Duration("code-ttl", 15*time.Minute, "配对码有效期")
	flag.Parse()

	st, err := store.Open(*dbPath)
	if err != nil {
		log.Fatalf("打开数据库失败: %v", err)
	}
	defer st.Close()

	srv := &http.Server{
		Addr:              *addr,
		Handler:           api.New(st, *codeTTL, time.Now).Handler(),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       120 * time.Second,
	}

	go func() {
		log.Printf("babycare-sync 启动 addr=%s db=%s", *addr, *dbPath)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("服务退出: %v", err)
		}
	}()

	// Ctrl+C / 服务停止时优雅退出，给在途请求 5 秒收尾
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	<-ctx.Done()
	log.Println("收到退出信号，正在关闭……")
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		log.Printf("优雅关闭失败: %v", err)
	}
}
