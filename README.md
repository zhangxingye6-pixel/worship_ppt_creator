<h1><img src="logo.jpg" alt="主日敬拜" style="position: relative; left: 50%; transform: translateX(-50%)"/></h1>

一个用于自动制作敬拜 PPT 文件的 Java 应用程序。

由马克约瑟弟兄制作，且目前由另一个上帝蒙爱的弟兄稍加升级。

![Version](https://img.shields.io/badge/version-v1.8.2-black)
[![GitHub license](https://img.shields.io/github/license/zhangxingye6-pixel/worship_ppt_creator)](https://github.com/zhangxingye6-pixel/worship_ppt_creator/blob/main/LICENSE)
![poi-ooxml dependency](https://img.shields.io/badge/poi--ooxml-v5.2.5-9cf)
![sqlite-jdbc dependency](https://img.shields.io/badge/sqlite--jdbc-3.39.2.0-9cf)

## 简介

为了更加快速、方便、准确地制作用于主日敬拜的 PPT 文件，此应用程序 __Worship PPT__ 为此而生。

此应用程序可以满足以下三种敬拜模式：

1. 常规
2. 圣餐
3. 入会

## 使用方式

该应用程序 __Worship PPT__ 基于 Java 17 运行。发布包已经自带对应平台的 Java 运行时，普通用户不需要另外安装 Java。
敬拜 PPT 文件工具提供 Windows、macOS Intel（amd64）和 macOS Apple Silicon（arm64）版本。

在 Windows 上打开 `Worship PPT.exe`；在 macOS 上打开 `Worship PPT.app`。

### 下载与版本更新

正式版本发布在 [GitHub Releases](https://github.com/zhangxingye6-pixel/worship_ppt_creator/releases) 页面。每个正式版本通常包含以下三个独立的 ZIP 压缩包：

- Windows
- macOS Intel（amd64）
- macOS Apple Silicon（arm64）

程序中的版本更新提示统一指向 GitHub Release 页面。macOS 版本目前没有代码签名和 Apple 公证，首次打开时可能需要在系统安全设置中允许运行。

### 开发与发布流程

日常修改在 `dev-update` 分支进行，完成测试后再合并到 `main`。正式版本只从 `main` 创建版本 tag，格式为 `v主版本号.次版本号.修订号`，例如 `v1.8.2`。发布 tag 前不需要手动修改 `pom.xml` 的版本号，构建流程会把 tag 作为版本号传给 Maven。

将正式 tag 推送到 GitHub 后，GitHub Actions 会自动执行以下流程：

1. 使用 `windows-latest` 构建 Windows 版本。
2. 使用 `macos-15-intel` 构建 macOS Intel（amd64）版本。
3. 使用 `macos-15` 构建 macOS Apple Silicon（arm64）版本，并校验实际 runner 架构。
4. 三个平台构建全部成功后，统一将三个 ZIP 上传到对应的 GitHub Release。

测试版本可以使用 `v1.8.2-rc.1` 这样的 RC tag。RC 会执行构建并保留 14 天的 GitHub Actions 构建产物，但不会创建或更新正式 Release。正式发布前应确认三个平台的构建均成功。

当前项目只使用 GitHub 作为代码仓库和发布平台，不再同步到 Codeberg。

## 高级用法

你可以修改外部配置文件、PPT 模板和数据库。程序运行时使用程序根目录下的 `config`、`PPT模板` 和 `数据库` 目录；这些目录需要与程序一起保留。

### 配置文件

程序固定读取程序根目录下 `config/worship-ppt.config`，不能在 GUI 中切换到其他配置路径。打包后的程序也使用这个外部配置文件，因此可以直接编辑它来调整设置。

`worship-ppt.config` 包含 PPT 尺寸、字号、间距、模板路径、数据库路径等选项。程序内置的 `core.properties` 只提供核心参数、资源路径、版本信息和 GitHub 更新检查参数，不应通过外部文件覆盖。

程序还会读取 `ppt.general.procedurePath` 指定的流程 XML；默认值为 `config/worship-procedure.xml`。该文件用于定义三种敬拜模式的步骤顺序和条件分支，是流程扩展入口，但每个步骤仍必须使用程序已经实现的步骤类。

### PPT 模板

敬拜 PPT 文件工具制作出来的 PPT，全部都是按照幻灯片母版来制作的，敬拜 PPT 文件工具无非就是把输入的敬拜数据填进去。

预设的 PPT 模板文件放在 `PPT模板` 文件夹里。`worship.pptx` 是常规模板，常规、圣餐和入会流程都会以它为基础；`initiation.pptx` 只用于入会阶段，程序会把其中指定的入会幻灯片复制到最终 PPT 中。

重要的事情再说一遍！`worship.pptx` 用的是幻灯片母版，所以，如果你要修改的话，则依次点击视图 -> 幻灯片母版，然后你会看到很多幻灯片版式。
你若修改一张幻灯片版式，那么使用该版式的所有幻灯片会全都变化。

### 敬拜流程

三种敬拜模式都有自己的流程，默认定义在 `config/worship-procedure.xml`。如果修改 `worship-step` 的顺序，最终 PPT 的页面顺序也会随之改变；如果修改流程文件路径，还要同步修改 `config/worship-ppt.config` 中的 `ppt.general.procedurePath`。

在该文件里，`model` 定义敬拜模式，`worship-step` 定义敬拜阶段。可以删除已有步骤，或通过 `if` 条件控制某个步骤是否生成；但新增步骤必须使用程序已有的步骤实现类，并提供对应的模板 layout 和数据表达式。

当前流程还支持公祷事项、家事报告、证道内容页、证道回应、宣信主题分支、大小要理问答，以及圣餐诗歌拆分等功能。公祷事项和家事报告属于当前生成任务的临时输入，不会写入敬拜配置缓存。修改流程 XML 时，应同时确认 GUI 提供的数据字段、PPT 母版 layout 名称和对应步骤类能够匹配。

### 圣经与宣信数据库

敬拜 PPT 文件工具用 SQLite 来存储圣经，以扩展名 `.db` 的文件默认放在 `数据库` 文件夹里。默认使用 `和合本.db`。
若要使用其它版本的圣经，可以在外部 `config/worship-ppt.config` 中修改 `bible.SQLite.path`。圣经数据库至少需要包含 `Bible` 和 `BookNames` 两张表，可以参考 `数据库/和合本.db` 的结构。

宣信内容使用 `信章信条.db`，西敏大要理问答和西敏小要理问答分别使用 `西敏大要理问答.db` 和 `西敏小要理问答.db`。大小要理数据库沿用统一的章、节查询结构：用户只输入题号范围，程序内部固定使用第 1 章以复用现有解析流程。切换宣信主题需要同时满足 GUI 选项、数据库内容、流程 XML 分支和 PPT layout 均已实现；仅修改配置文件不会自动增加新的业务支持。

### 外部文件

发布包依赖程序根目录下的外部 `config`、`PPT模板` 和 `数据库` 目录。程序启动时会读取外部 `config/worship-ppt.config`；核心模板、版本信息和 GitHub 更新检查参数来自 JAR 内置的 `core.properties`，用户配置不能覆盖这些核心参数。

如果发布包中的 `config`、PPT 模板或数据库缺失，或外部流程 XML 与当前程序步骤类、layout 名称不匹配，程序可能无法正常启动或无法生成对应页面。
