#!/bin/bash
set -e

echo "=== Bắt đầu quá trình tự động cập nhật Backend CASMS ==="

cd /home/ubuntu/casms

# 1. Dừng container cũ nếu đang chạy
echo "Dừng các container hiện tại..."
docker compose down || true

# 2. Build lại image mới từ code vừa nhận và chạy ngầm
echo "Build lại Docker image và khởi chạy..."
docker compose up -d --build

# 3. Dọn dẹp image cũ không dùng để tiết kiệm dung lượng ổ cứng
echo "Dọn dẹp image cũ..."
docker image prune -f

echo "=== Triển khai hoàn tất thành công! ==="

