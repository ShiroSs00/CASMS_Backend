#!/bin/bash
set -e

echo "=== Bắt đầu quá trình tự động cập nhật Backend CASMS ==="

cd /home/ubuntu/casms

# 1. Dọn dẹp build cache docker cũ để giải phóng dung lượng đĩa
echo "Dọn dẹp build cache cũ để đảm bảo đủ dung lượng..."
docker builder prune -f || true

# 2. Dừng container cũ nếu đang chạy
echo "Dừng các container hiện tại..."
docker compose down || true

# 3. Build lại image mới từ code vừa nhận và khởi chạy
echo "Build lại Docker image và khởi chạy..."
docker compose up -d --build

# 4. Dọn dẹp image cũ không dùng để tiết kiệm dung lượng ổ cứng
echo "Dọn dẹp image cũ..."
docker image prune -f

echo "=== Triển khai hoàn tất thành công! ==="
