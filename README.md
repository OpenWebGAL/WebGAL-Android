# WebGAL Android

一个将 WebGAL 游戏打包到安卓平台的简易模板

本模板使用的图标来自：[MakinoharaShoko/WebGAL](https://github.com/MakinoharaShoko/WebGAL)

## 如何打包游戏

* 安装 android studio 并导入本项目
* 将游戏移动到 `app\src\main\assets\webgal`。默认加载 `app\src\main\assets\webgal\index.html`，如有需要自定义加载链接请修改 `app\src\main\res\values\values.xml` 文件里面的 `load_url` 字段
* 更改包名以及游戏名和图标
* 点击菜单栏 `Build` -> `Generate Signed Bundle or APK` 构建 apk

### 详细的打包步骤

[打包 WebGAL 游戏到 Android 平台](https://nini22p.github.io/post/webgal-for-android/)
