# iPhone 桥接

在你的 Android 设备上接收来自 iOS 设备的通知

# 快速上手

## iOS (发送端)

1. 在 App Store 上搜索 `LightBlue`，一个低功耗蓝牙调试应用。
2. 选择底部 `Virtual Device` 选项卡。
3. 创建一个虚拟设备，类型选择 `Heart Rate`。
4. 创建成功后，确保 `Heart Rate` 处于选中状态。保持您的 iOS 设备不要熄屏，且 LightBlue 正处于前台工作。

## Android (接收端)

5. 从 [GitHub Release](https://github.com/xiaoheiCat/iPhone-Bridge/releases) 获取最新版本的 `iPhone 桥接` APK 安装包，并将其安装到您的 Android 设备上。
6. 打开 `iPhone 桥接`，授予所有权限后，点击 `扫描设备`。
7. 当提示发现 `Heart Rate` 设备时，点击 `连接` 即可完成设置。

## iOS (发送端)

8. 最后，请在 iOS 设备上同意系统通知共享请求，配置完成。

# 参考资料

[Android BLE开发之操作IOS ANCS](https://www.jianshu.com/p/88858b8e5e67)

[Android BLE开发之初识GATT](https://www.jianshu.com/p/29a730795294)

[苹果通知中心服务ANCS协议分析](https://www.jianshu.com/p/2ddf76ab85b0)

[苹果通知中心服务ANCS协议分析二](https://www.jianshu.com/p/b82db7b6312f)

[brookwillow/ANCSReader · GitHub](https://github.com/brookwillow/ANCSReader)

[Apple Notification Center Service (ANCS) Specification Documents](https://developer.apple.com/library/archive/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Specification/Specification.html#//apple_ref/doc/uid/TP40013460-CH1-SW7)

# Star 历史

[![Star 历史图表](https://api.star-history.com/svg?repos=xiaoheiCat/iPhone-Bridge&type=Date)](https://www.star-history.com/#xiaoheiCat/iPhone-Bridge&Date)

# 特别感谢

- 小时不识月z (简书) / brookwillow (GitHub)

- Claude 大人 (没有它，也许就没有这个项目 TwT)
