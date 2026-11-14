<h1><img src="logo.jpg" alt="主日敬拜" style="position: relative; left: 50%; transform: translateX(-50%)"/></h1>

一个用于自动制作敬拜 PPT 文件的 Java 应用程序。

由马克约瑟弟兄制作，且目前由另一个上帝蒙爱的弟兄稍加升级。

![Version](https://img.shields.io/badge/version-v1.8.2-black)
[![GitHub license](https://img.shields.io/github/license/ClayGminx/worship-ppt)](https://github.com/ClayGminx/worship-ppt/blob/master/LICENSE)
![poi-ooxml dependency](https://img.shields.io/badge/poi--ooxml-v5.2.2-9cf)
![sqlite-jdbc dependency](https://img.shields.io/badge/sqlite--jdbc-3.39.2.0-9cf)

## 简介

为了更加快速、方便、准确地制作用于主日敬拜的 PPT 文件，此应用程序 __Worship PPT__ 为此而生。

此应用程序可以满足以下三种敬拜模式：

1. 常规
2. 圣餐
3. 入会

## 使用方式

该应用程序 __Worship PPT__ 必须要在 Java 虚拟机上运行。但是，为了减少繁琐的安装操作，敬拜PPT文件工具已经自带了 Java 虚拟机。
敬拜PPT文件工具分为 Windows 版和 Mac OS 版。

在 Windows 上，你只需要打开`Worship PPT.exe`。在 Mac上运行`Worship PPT.app`

### 下载与版本更新

正式版本发布在 [GitHub Releases](https://github.com/zhangxingye6-pixel/worship_ppt_creator/releases) 页面。每个正式版本包含以下三个独立的 ZIP 安装包：

- Windows
- macOS Intel（amd64）
- macOS Apple Silicon（arm64）

程序中的版本更新提示统一指向 GitHub Release 页面。

### 开发与发布流程

日常修改在 `dev-update` 分支进行，完成测试后再合并到 `main`。正式版本只从 `main` 创建版本 tag，格式为 `v主版本号.次版本号.修订号`，例如 `v1.8.2`。

将正式 tag 推送到 GitHub 后，GitHub Actions 会自动执行以下流程：

1. 使用 `windows-latest` 构建 Windows 版本。
2. 使用 `macos-15-intel` 构建 macOS Intel（amd64）版本。
3. 使用 `macos-15` 构建 macOS Apple Silicon（arm64）版本，并校验实际 runner 架构。
4. 三个平台构建全部成功后，统一将三个 ZIP 上传到对应的 GitHub Release。

测试版本可以使用 `v1.8.2-rc.1` 这样的 RC tag。RC 会执行构建并保留 GitHub Actions 构建产物，但不会上传到正式 Release。正式发布前应确认三个平台的构建均成功。

当前项目只使用 GitHub 作为代码仓库和发布平台，不再同步到 Codeberg。

## 高级用法

你可以修改预设好的配置文件、PPT模板、圣经数据库。

### 配置文件

你可以从菜单栏中依次点击选项->自定义配置，输入你的配置文件的完全路径或相对路径。默认配置文件位于“config”文件夹里。

`worship-ppt.config`有大量的配置选项。你可以修改制作PPT文件的参数、更换圣经数据，以及更多配置选项; 有大量注释教你怎么修改，相信你看得懂。

预留了一些用户接口，比如宣信阶段，可以通过配置修改宣信主题（如西敏信条、…………）

### PPT模板

敬拜PPT文件工具制作出来的PPT，全部都是按照幻灯片母版来制作的，敬拜PPT文件工具无非就是把输入的敬拜数据填进去。

预设的PPT模板文件放在“PPT模板”文件夹里，里面有两个PPT文件。`worship.pptx`是常规PPT模板文件，三种敬拜模式都会使用它。
`initiation.pptx`是入会模板文件。制作带有入会环节的PPT文件时，程序会先使用`worship.pptx`，若有入会，则将`initiation.pptx`里的幻灯片拷贝进来。

重要的事情再说一遍！`worship.pptx`用的是幻灯片母版，所以，如果你要修改的话，则依次点击视图->幻灯片母版，然后你会看到很多幻灯片版式。
你若修改一张幻灯片版式，那么使用该版式的所有幻灯片会全都变化。

### 敬拜流程

三种敬拜模式都有自己的流程，它们的流程定义在`worship-procedure.xml`。如果修改标签的顺序，制作而成的PPT也会随之修改

在该文件里，`model`定义了敬拜模式，而`worship-step`定义了敬拜阶段，你可以在该文件删除一个`worship-step`，
但是若要增加一个worship-ppt尚未支持的新阶段，就会因不被支持而出错。（不妨多为软件作者祈祷，制作出一个更加通用的软件来。）

### 圣经

敬拜PPT文件工具用 SQLite 来存储圣经，以扩展名`.db`的文件格式默认放在“数据库”文件夹里。默认使用“和合本”。
若要使用其它版本的圣经，你可以在`worship-ppt.properties`中修改`SQLite.path`值。（目前还没有其他版本的数据库呦）
注意，圣经数据库要有`Bible`和`BookNames`两张表，不妨参考“和合本.db”来制作。

为了适应当前的敬拜流程，新建了宣信内容的数据库`信章信条.db`；信条总会有讲完的一天，所以如果需要切换宣信主题，可以在配置中进行修改。但前提是已经实现了相关的业务代码。当前版本为了应急，扩展性不是特别好，需要进一步的更新
