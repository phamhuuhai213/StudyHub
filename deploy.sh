#!/bin/bash

# ===================================
# STUDYHUB DEPLOY SCRIPT
# Chỉnh sửa VPS_IP và VPS_USER cho đúng
# ===================================

VPS_IP="161.35.57.163"        # ← Thay bằng IP VPS của bạn
VPS_USER="root"
REMOTE_DIR="/opt/studyhub"
JAR_NAME="StudyHub-0.0.1-SNAPSHOT.jar"

echo "🔨 [1/3] Building JAR..."
./mvnw clean package -DskipTests -q
if [ $? -ne 0 ]; then
    echo "❌ Build thất bại!"
    exit 1
fi
echo "✅ Build thành công!"

echo "📦 [2/3] Uploading lên VPS..."
scp target/$JAR_NAME $VPS_USER@$VPS_IP:$REMOTE_DIR/app.jar
if [ $? -ne 0 ]; then
    echo "❌ Upload thất bại!"
    exit 1
fi
echo "✅ Upload thành công!"

echo "🚀 [3/3] Restart service..."
ssh $VPS_USER@$VPS_IP "systemctl restart studyhub && sleep 3 && systemctl is-active studyhub"
if [ $? -ne 0 ]; then
    echo "❌ Restart thất bại! Kiểm tra log: journalctl -u studyhub -n 50"
    exit 1
fi

echo ""
echo "✅ Deploy hoàn tất! Truy cập: https://$VPS_IP"
