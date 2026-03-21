使用 OSS Java SDK V2 在 Java 应用中接入阿里云对象存储 OSS，实现文件的上传、下载和管理功能，适合网站、企业和开发者进行云端文件存储操作。

[Github](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2) | [OSS SDK for Java API](https://javadoc.io/doc/com.aliyun/alibabacloud-oss-v2/latest/index.html) | [mvnrepository](https://mvnrepository.com/artifact/com.aliyun/alibabacloud-oss-v2) | [deepwiki](https://deepwiki.com/aliyun/alibabacloud-oss-java-sdk-v2/1-alibaba-cloud-oss-java-sdk-v2-overview)

## 快速接入

接入OSS Java SDK V2的流程如下：

!\[image]\(https\://help-static-aliyun-doc.aliyuncs.com/assets/img/zh-CN/3844173771/CAEQYRiBgIClh8bsxRkiIDIxNDJjMDMxNTM0ZTRlMDU5NGYyMWU0YmRiOTMyYTRi5272737\_20250624105943.598.svg null)

### 环境准备

Java 8 及以上版本。

> 通过`java -version`命令查看 Java 版本。如果当前环境没有 Java 或版本低于 Java 8，请[下载并安装Java](https://www.oracle.com/java/technologies/downloads/)。

### **安装SDK**

建议使用 Maven 方式安装 OSS Java SDK V2。

## Maven

在 `pom.xml` 添加如下依赖，并将 `<version>` 替换为在 [Maven Repository](https://mvnrepository.com/artifact/com.aliyun/alibabacloud-oss-v2) 查询到的最新版本号：

```
<dependency>
    <groupId>com.aliyun</groupId>
    <artifactId>alibabacloud-oss-v2</artifactId>
    <version><!-- 填写最新版本号--></version>
</dependency>
```

## **源码**

从 [Github](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2) 获取最新版本的 OSS Java SDK V2，并通过 Maven 构建和安装：

```
mvn clean install -DskipTests -Dgpg.skip=true
```

### **配置访问凭证**

将 RAM 用户的 AccessKey 写入环境变量作为凭证。

> 在[RAM 控制台](https://ram.console.aliyun.com/users/create)，创建**使用永久 AccessKey 访问**的 RAM 用户，保存 AccessKey，然后为该用户授予`AliyunOSSFullAccess`权限。

## Linux

1. 在命令行界面执行以下命令来将环境变量设置追加到`~/.bashrc` 文件中。
   ```
   echo "export OSS_ACCESS_KEY_ID='YOUR_ACCESS_KEY_ID'" >> ~/.bashrc
   echo "export OSS_ACCESS_KEY_SECRET='YOUR_ACCESS_KEY_SECRET'" >> ~/.bashrc
   ```
   1. 执行以下命令使变更生效。
      ```
      source ~/.bashrc
      ```
   2. 执行以下命令检查环境变量是否生效。
      ```
      echo $OSS_ACCESS_KEY_ID
      echo $OSS_ACCESS_KEY_SECRET
      ```

## macOS

1. 在终端中执行以下命令，查看默认Shell类型。
   ```
   echo $SHELL
   ```
   1. 根据默认Shell类型进行操作。
      #### **Zsh**
      1. 执行以下命令来将环境变量设置追加到`~/.zshrc`文件中。
         ```
         echo "export OSS_ACCESS_KEY_ID='YOUR_ACCESS_KEY_ID'" >> ~/.zshrc
         echo "export OSS_ACCESS_KEY_SECRET='YOUR_ACCESS_KEY_SECRET'" >> ~/.zshrc
         ```
      2. 执行以下命令使变更生效。
         ```
         source ~/.zshrc
         ```
      3. 执行以下命令检查环境变量是否生效。
         ```
         echo $OSS_ACCESS_KEY_ID
         echo $OSS_ACCESS_KEY_SECRET
         ```
      #### **Bash**
      1. 执行以下命令来将环境变量设置追加到`~/.bash_profile`文件中。
         ```
         echo "export OSS_ACCESS_KEY_ID='YOUR_ACCESS_KEY_ID'" >> ~/.bash_profile
         echo "export OSS_ACCESS_KEY_SECRET='YOUR_ACCESS_KEY_SECRET'" >> ~/.bash_profile
         ```
      2. 执行以下命令使变更生效。
         ```
         source ~/.bash_profile
         ```
      3. 执行以下命令检查环境变量是否生效。
         ```
         echo $OSS_ACCESS_KEY_ID
         echo $OSS_ACCESS_KEY_SECRET
         ```

## Windows

## CMD

1. 在CMD中运行以下命令。
   ```
   setx OSS_ACCESS_KEY_ID "YOUR_ACCESS_KEY_ID"
   setx OSS_ACCESS_KEY_SECRET "YOUR_ACCESS_KEY_SECRET"
   ```
   1. 运行以下命令，检查环境变量是否生效。
      ```
      echo %OSS_ACCESS_KEY_ID%
      echo %OSS_ACCESS_KEY_SECRET%
      ```

## PowerShell

1. 在PowerShell中运行以下命令。
   ```
   [Environment]::SetEnvironmentVariable("OSS_ACCESS_KEY_ID", "YOUR_ACCESS_KEY_ID", [EnvironmentVariableTarget]::User)
   [Environment]::SetEnvironmentVariable("OSS_ACCESS_KEY_SECRET", "YOUR_ACCESS_KEY_SECRET", [EnvironmentVariableTarget]::User)
   ```
   1. 运行以下命令，检查环境变量是否生效。
      ```
      [Environment]::GetEnvironmentVariable("OSS_ACCESS_KEY_ID", [EnvironmentVariableTarget]::User)
      [Environment]::GetEnvironmentVariable("OSS_ACCESS_KEY_SECRET", [EnvironmentVariableTarget]::User)
      ```

### 初始化客户端

- OSSClient 实现了 AutoCloseable，新建实例，采用 try resource 方式使用时，会自动释放资源，无需手动调用 close。
- OSSClient 创建和销毁是耗时的，可采用单例模式，复用 OSSClient，但应用终止前必须手动调用 close，否则资源会泄漏。

  **单例模式**
  > 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。
  ```
  public class OssClientSingleton {
      private OssClientSingleton() {}

      private static class Holder {
          private static final OSSClient INSTANCE = OSSClient.newBuilder()
              .credentialsProvider(new EnvironmentVariableCredentialsProvider())
              .region("<region-id>")
              .build();
      }

      public static OSSClient getInstance() {
          return Holder.INSTANCE;
      }

      // 关闭 OSSClient（需显式调用）
      public static void shutdown() {
          try {
              getInstance().close();
          } catch (Exception e) {
              // 处理关闭异常
          }
      }
  }
  ```

## 同步 OSSClient

如果需要等操作执行完成后再进行后续处理，选择同步 OSSClient。

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.OSSClientBuilder;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;
import com.aliyun.sdk.service.oss2.exceptions.ServiceException;
import com.aliyun.sdk.service.oss2.models.*;
import com.aliyun.sdk.service.oss2.paginator.ListBucketsIterable;

public class Example {
    public static void main(String[] args) {
        String region = "<region-id>";

        CredentialsProvider provider = new EnvironmentVariableCredentialsProvider();
        OSSClientBuilder clientBuilder = OSSClient.newBuilder()
                .credentialsProvider(provider)
                .region(region);

        try (OSSClient client = clientBuilder.build()) {

            ListBucketsIterable paginator = client.listBucketsPaginator(
                    ListBucketsRequest.newBuilder()
                            .build());

            for (ListBucketsResult result : paginator) {
                for (BucketSummary info : result.buckets()) {
                    System.out.printf("bucket: name:%s, region:%s, storageClass:%s\n", info.name(), info.region(), info.storageClass());
                }
            }

        } catch (Exception e) {
//            ServiceException se = ServiceException.asCause(e);
//            if (se != null) {
//                System.out.printf("ServiceException: requestId:%s, errorCode:%s\n", se.requestId(), se.errorCode());
//            }
            System.out.printf("error:\n%s", e);
        }
    }
}
```

## 异步 OSSClient

如果需要并发处理多个 OSS 操作，且不依赖每个操作的结果，可以选择异步 OSSClient。

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.OSSAsyncClient;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;
import com.aliyun.sdk.service.oss2.exceptions.ServiceException;
import com.aliyun.sdk.service.oss2.models.*;

import java.util.concurrent.CompletableFuture;

public class ExampleAsync {
    public static void main(String[] args) {
        String region = "<region-id>";
        CredentialsProvider provider = new EnvironmentVariableCredentialsProvider();

        try (OSSAsyncClient client = OSSAsyncClient.newBuilder()
                .region(region)
                .credentialsProvider(provider)
                .build()) {

            CompletableFuture<ListBucketsResult> future = client.listBucketsAsync(
                    ListBucketsRequest.newBuilder().build()
            );

            future.thenAccept(result -> {
                        for (BucketSummary info : result.buckets()) {
                            System.out.printf("bucket: name:%s, region:%s, storageClass:%s\n",
                                    info.name(), info.region(), info.storageClass());
                        }
                    })
                    .exceptionally(e -> {
//                ServiceException se = ServiceException.asCause(e);
//                if (se != null) {
//                    System.out.printf("Async ServiceException: requestId:%s, errorCode:%s\n",
//                            se.requestId(), se.errorCode());
//                }
                        System.out.printf("async error:\n%s\n", e);
                        return null;
                    });

            future.join();

        } catch (Exception e) {
            System.out.printf("main error:\n%s\n", e);
        }
    }
}
```

运行后将会看到当前账号在所有地域下的 Bucket。

## 客户端配置

**客户端支持哪些配置？**

| **参数名**               | **说明**                           |
| :-------------------- | :------------------------------- |
| region                | (必选)请求发送的区域，必选                   |
| credentialsProvider   | (必选)设置访问凭证                       |
| endpoint              | 访问域名                             |
| httpClient            | HTTP客户端                          |
| retryMaxAttempts      | HTTP请求时的最大尝试次数，默认值为 3            |
| retryer               | HTTP请求时的重试实现                     |
| connectTimeout        | 建立连接的超时时间，默认值为 5 秒               |
| readWriteTimeout      | 应用读写数据的超时时间，默认值为 20 秒            |
| insecureSkipVerify    | 是否跳过SSL证书校验，默认检查SSL证书            |
| enabledRedirect       | 是否开启HTTP重定向，默认不开启                |
| proxyHost             | 设置代理服务器                          |
| signatureVersion      | 签名版本，默认值为v4                      |
| disableSsl            | 不使用https请求，默认使用https             |
| usePathStyle          | 使用路径请求风格，即二级域名请求风格，默认为bucket托管域名 |
| useCName              | 是否使用自定义域名访问，默认不使用                |
| useDualStackEndpoint  | 是否使用双栈域名访问，默认不使用                 |
| useAccelerateEndpoint | 是否使用传输加速域名访问，默认不使用               |
| useInternalEndpoint   | 是否使用内网域名访问，默认不使用                 |
| additionalHeaders     | 指定额外的签名请求头，V4签名下有效               |
| userAgent             | 指定额外的User-Agent信息                |

### 使用自定义域名

使用OSS默认域名访问时，可能会出现文件禁止访问、文件无法预览等问题；通过[通过自定义域名访问OSS](https://help.aliyun.com/zh/oss/user-guide/access-buckets-via-custom-domain-names#concept-zt4-cvy-5db)，不仅支持浏览器直接预览文件，还可结合CDN加速分发。

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.*;
import com.aliyun.sdk.service.oss2.credentials.*;

public class Example {
    public static void main(String[] args) {
        // 从环境变量中加载凭证信息，用于身份验证
        CredentialsProvider credentialsProvider = new EnvironmentVariableCredentialsProvider();

        // 填写Bucket所在地域。
        String region = "<region-id>";

        // 请填写您的自定义域名。例如www.example-***.com
        String endpoint = "https://www.example-***.com";

        // 使用配置好的信息创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .endpoint(endpoint)
                // 请注意，设置true开启CNAME选项，否则无法使用自定义域名
                .useCName(true)
                .build()) {

            // 使用创建好的client执行后续操作...

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
        }
    }
}
```

### 超时控制

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.*;
import com.aliyun.sdk.service.oss2.credentials.*;
import java.time.Duration;

public class Example {
    public static void main(String[] args) {
        // 从环境变量中加载凭证信息，用于身份验证
        CredentialsProvider credentialsProvider = new EnvironmentVariableCredentialsProvider();

        // 填写Bucket所在地域。
        String region = "<region-id>";

        // 使用配置好的信息创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                // 设置建立连接的超时时间, 默认值 5秒
                .connectTimeout(Duration.ofSeconds(30))
                // 设置应用读写数据的超时时间, 默认值 20秒
                .readWriteTimeout(Duration.ofSeconds(30))
                .build()) {

            // 使用创建好的client执行后续操作...

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
        }
    }
}
```

### 重试策略

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.*;
import com.aliyun.sdk.service.oss2.credentials.*;
import com.aliyun.sdk.service.oss2.retry.*;
import java.time.Duration;

public class Example {
    public static void main(String[] args) {
        /*
         * SDK 重试策略配置说明：
         *
         * 默认重试策略：
         * 当没有配置重试策略时，SDK 使用 StandardRetryer 作为客户端的默认实现，其默认配置如下：
         * - maxAttempts：设置最大尝试次数。默认为3次
         * - maxBackoff：设置最大退避时间（单位：秒）。默认为20秒
         * - baseDelay：设置基础延迟时间（单位：秒）。默认为0.2秒
         * - backoffDelayer：设置退避算法。默认使用FullJitter退避算法
         *   计算公式为：[0.0, 1.0) * min(2^attempts * baseDelay, maxBackoff)
         * - errorRetryables：可重试的错误类型，包括HTTP状态码、服务错误码、客户端错误等
         *
         * 当发生可重试错误时，将使用其提供的配置来延迟并随后重试该请求。
         * 请求的总体延迟会随着重试次数而增加，如果默认配置不满足您的场景需求时，
         * 可配置重试参数或者修改重试实现。
         */

        // 从环境变量中加载凭证信息，用于身份验证
        CredentialsProvider credentialsProvider = new EnvironmentVariableCredentialsProvider();

        // 填写Bucket所在地域。
        String region = "<region-id>";

        // 配置重试策略示例：

        // 1. 自定义最大重试次数（默认为3次，这里设置为5次）
        Retryer customRetryer = StandardRetryer.newBuilder()
                .maxAttempts(5)
                .build();

        // 2. 自定义退避延迟时间
        // 调整基础延迟时间 baseDelay 为0.5秒（默认0.2秒），最大退避时间 maxBackoff 为25秒（默认20秒）
        // Retryer customRetryer = StandardRetryer.newBuilder()
        //         .backoffDelayer(new FullJitterBackoff(Duration.ofMillis(500), Duration.ofSeconds(25)))
        //         .build();

        // 3. 自定义退避算法
        // 使用固定时间退避算法替代默认的FullJitter算法，每次延迟2秒
        // Retryer customRetryer = StandardRetryer.newBuilder()
        //         .backoffDelayer(new FixedDelayBackoff(Duration.ofSeconds(2)))
        //         .build();

        // 4. 禁用重试策略
        // 如需禁用所有重试尝试时，可以使用NopRetryer实现
        // Retryer customRetryer = new NopRetryer();

        // 使用配置好的信息创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .retryer(customRetryer)
                .build()) {

            // 使用创建好的client执行后续操作...

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
        }
    }
}
```

### HTTP/HTTPS 协议

使用`disableSsl(true)`设置不使用HTTPS协议。

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.*;
import com.aliyun.sdk.service.oss2.credentials.*;

public class Example {
    public static void main(String[] args) {
        // 从环境变量中加载凭证信息，用于身份验证
        CredentialsProvider credentialsProvider = new EnvironmentVariableCredentialsProvider();

        // 填写Bucket所在地域。
        String region = "<region-id>";

        // 使用配置好的信息创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                // 设置不使用https请求
                .disableSsl(true)
                .build()) {

            // 使用创建好的client执行后续操作...

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
        }
    }
}
```

### 使用内网域名

使用内网域名访问同地域的OSS资源，可以降低流量成本并提高访问速度。

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.*;
import com.aliyun.sdk.service.oss2.credentials.*;

public class Example {
    public static void main(String[] args) {
        // 从环境变量中加载凭证信息，用于身份验证
        CredentialsProvider credentialsProvider = new EnvironmentVariableCredentialsProvider();

        // 方式一： 填写Region并设置useInternalEndpoint为true
        // 填写Bucket所在地域。
        String region = "<region-id>";

        // // 方式二： 直接填写Region和Endpoint
        // // 填写Bucket所在地域。
        // String region = "<region-id>";
        // // 填写Bucket所在地域对应的内网Endpoint。
        // String endpoint = "<endpoint>";

        // 使用配置好的信息创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .useInternalEndpoint(true)
                // .endpoint(endpoint) // 如果使用方式二，取消注释此行并注释上一行
                .build()) {

            // 使用创建好的client执行后续操作...

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
        }
    }
}
```

### 使用传输加速域名

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.*;
import com.aliyun.sdk.service.oss2.credentials.*;

public class Example {
    public static void main(String[] args) {
        // 从环境变量中加载凭证信息，用于身份验证
        CredentialsProvider credentialsProvider = new EnvironmentVariableCredentialsProvider();

        // 方式一： 填写Region并设置useAccelerateEndpoint为true
        // 填写Bucket所在地域。
        String region = "<region-id>";

        // // 方式二： 直接填写Region和传输加速Endpoint
        // // 填写Bucket所在地域。
        // String region = "<region-id>";
        // // 填写Bucket所在地域对应的传输加速Endpoint，例如'https://oss-accelerate.aliyuncs.com'
        // String endpoint = "https://oss-accelerate.aliyuncs.com";

        // 使用配置好的信息创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .useAccelerateEndpoint(true)
                // .endpoint(endpoint) // 如果使用方式二，取消注释此行并注释上一行
                .build()) {

            // 使用创建好的client执行后续操作...

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
        }
    }
}
```

### 使用专有域

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.*;
import com.aliyun.sdk.service.oss2.credentials.*;

public class Example {
    public static void main(String[] args) {
        // 从环境变量中加载凭证信息，用于身份验证
        CredentialsProvider credentialsProvider = new EnvironmentVariableCredentialsProvider();

        // 填写Bucket所在地域。
        String region = "<region-id>";

        // 请填写您的专有域。例如：https://service.corp.example.com
        String endpoint = "https://service.corp.example.com";

        // 使用配置好的信息创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .endpoint(endpoint)
                .build()) {

            // 使用创建好的client执行后续操作...

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
        }
    }
}
```

### 使用金融云域名

以下是使用[金融云](https://help.aliyun.com/zh/oss/regions-and-endpoints/#dbe294402aq6j)域名配置OSSClient的示例代码。

```
import com.aliyun.sdk.service.oss2.*;
import com.aliyun.sdk.service.oss2.credentials.*;

public class Example {
    public static void main(String[] args) {
        // 从环境变量中加载凭证信息，用于身份验证
        CredentialsProvider credentialsProvider = new EnvironmentVariableCredentialsProvider();

        // 填写Region和Endpoint
        // 填写Bucket所在地域。以华东1 金融云为例，Region填写为cn-hangzhou-finance
        String region = "cn-hangzhou-finance";
        // 填写Bucket所在地域对应的内网Endpoint。以华东1 金融云为例，Endpoint填写为'https://oss-cn-hzjbp-a-internal.aliyuncs.com',
        // 如需指定为http协议，请在指定域名时填写为'http://oss-cn-hzjbp-a-internal.aliyuncs.com'
        String endpoint = "https://oss-cn-hzjbp-a-internal.aliyuncs.com";

        // 使用配置好的信息创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .endpoint(endpoint)
                .build()) {

            // 使用创建好的client执行后续操作...

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
        }
    }
}
```

### 使用政务云域名

以下是使用[政务云](https://help.aliyun.com/zh/oss/regions-and-endpoints/#3e2a1817f0pps)域名配置OSSClient的示例代码。

```
import com.aliyun.sdk.service.oss2.*;
import com.aliyun.sdk.service.oss2.credentials.*;

public class Example {
    public static void main(String[] args) {
        // 从环境变量中加载凭证信息，用于身份验证
        CredentialsProvider credentialsProvider = new EnvironmentVariableCredentialsProvider();

        // 填写Region和Endpoint
        // 填写Bucket所在地域。以华北2 阿里政务云1为例，Region填写为cn-north-2-gov-1
        String region = "cn-north-2-gov-1";
        // 填写Bucket所在地域对应的内网Endpoint。以华北2 阿里政务云1为例，Endpoint填写为'https://oss-cn-north-2-gov-1-internal.aliyuncs.com',
        // 如需指定为http协议，请在指定域名时填写为'http://oss-cn-north-2-gov-1-internal.aliyuncs.com'
        String endpoint = "https://oss-cn-north-2-gov-1-internal.aliyuncs.com";

        // 使用配置好的信息创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .endpoint(endpoint)
                .build()) {

            // 使用创建好的client执行后续操作...

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
        }
    }
}
```

### 自定义HTTPClient

当常用配置参数无法满足场景需求时，您可以使用自定义 HTTP 客户端。

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

**说明**

以下示例展示了同步客户端（`OSSClient`）的自定义 HTTPClient 配置。如果您使用异步客户端（`OSSAsyncClient`），请将 `Apache5HttpClientBuilder` 替换为 `Apache5AsyncHttpClientBuilder`，其他配置参数相同。

```
import com.aliyun.sdk.service.oss2.*;
import com.aliyun.sdk.service.oss2.credentials.*;
import com.aliyun.sdk.service.oss2.transport.HttpClient;
import com.aliyun.sdk.service.oss2.transport.HttpClientOptions;
import com.aliyun.sdk.service.oss2.transport.apache5client.Apache5HttpClientBuilder;
import java.time.Duration;

public class Example {
    public static void main(String[] args) {
        // 从环境变量中加载凭证信息，用于身份验证
        CredentialsProvider credentialsProvider = new EnvironmentVariableCredentialsProvider();

        // 填写Bucket所在地域。
        String region = "<region-id>";

        // 设置HTTP客户端的参数
        HttpClientOptions httpClientOptions = HttpClientOptions.custom()
                // 连接超时, 默认值 5秒
                .connectTimeout(Duration.ofSeconds(30))
                // 应用读写数据的超时时间, 默认值 20秒
                .readWriteTimeout(Duration.ofSeconds(30))
                // 最大连接数，默认值 1024
                .maxConnections(2048)
                // 是否跳过证书检查，默认不跳过
                .insecureSkipVerify(false)
                // 是否打开启HTTP重定向，默认不启用
                .redirectsEnabled(false)
                // 设置代理服务器
                // .proxyHost("http://user:passswd@proxy.example-***.com")
                .build();

        // 创建HTTP客户端，并传入HTTP客户端参数
        HttpClient httpClient = Apache5HttpClientBuilder.create()
                .options(httpClientOptions)
                .build();

        // 使用配置好的信息创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .httpClient(httpClient)
                .build()) {

            // 使用创建好的client执行后续操作...

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
        }
    }
}
```

## 访问凭证配置

阿里云对象存储 OSS Java SDK V2 提供多种访问凭证配置方式。请根据您的认证和授权需求选择合适的初始化方式。

#### **如何选择访问凭证？**

| **凭证提供者初始化方式**                    | **适用场景**                                      | **Java SDK V2支持情况** | **底层实现基于的凭证** | **凭证有效期** | **凭证轮转或刷新方式** |
| :-------------------------------- | :-------------------------------------------- | :------------------ | :------------ | :-------- | :------------ |
| [使用RAM用户的AK](#使用ram用户的ak)         | 部署运行在安全、稳定且不易受外部攻击的环境的应用程序，无需频繁轮转凭证就可以长期访问云服务 | **内置支持**            | AK            | 长期        | 手动轮转          |
| [使用STS临时访问凭证](#使用sts临时访问凭证)       | 部署运行在不可信的环境的应用程序，希望能控制访问的有效期、权限               | **内置支持**            | STS Token     | 临时        | 手动刷新          |
| [使用RAMRoleARN凭证](#9d4af14290wu9)  | 跨账号访问OSS资源，需要通过扮演RAM角色获取临时凭证                  | **扩展支持**            | STS Token     | 临时        | 自动刷新          |
| [使用ECSRAMRole凭证](#d6c272ca310w1)  | 运行在ECS实例、ECI实例或容器服务Kubernetes版的应用程序           | **扩展支持**            | STS Token     | 临时        | 自动刷新          |
| [使用OIDCRoleARN凭证](#6580c4f8c1kk0) | 容器服务Kubernetes版中的RRSA功能，实现Pod级别的权限隔离          | **扩展支持**            | STS Token     | 临时        | 自动刷新          |
| [使用自定义访问凭证](#使用自定义访问凭证)           | 如果以上凭证配置方式都不满足要求时，您可以自定义获取凭证的方式               | **内置支持**            | 自定义           | 自定义       | 自定义           |
| [匿名访问](#匿名访问)                     | 访问公共读取权限的OSS资源，无需提供任何凭证                       | **内置支持**            | 无             | 无         | 无             |

标注为**扩展支持**的凭证功能需要通过[自定义访问凭证](#使用自定义访问凭证)方式结合阿里云凭证管理库来实现。SDK内置支持基础的凭证配置方式，扩展功能可通过集成`credentials-java`库实现。

### 使用RAM用户的AK

如果您的应用程序部署运行在安全、稳定且不易受外部攻击的环境中，需要长期访问您的OSS，且不能频繁轮转凭证时，您可以使用阿里云主账号或RAM用户的AK（Access Key ID、Access Key Secret）初始化凭证提供者。需要注意的是，该方式需要您手动维护一个AK，存在安全性风险和维护复杂度增加的风险。

**重要**

- 阿里云账号拥有资源的全部权限，AK一旦泄露，会给系统带来巨大风险，不建议使用。推荐使用最小化授权的RAM用户的AK。
- 如需创建RAM用户的AK，请直接访问[创建AccessKey](https://help.aliyun.com/zh/ram/create-an-accesskey-pair-1)。RAM用户的Access Key ID、Access Key Secret信息仅在创建时显示，请及时保存，如若遗忘请考虑创建新的AK进行轮换。

#### 环境变量配置

## Linux/macOS

1. 使用RAM用户AccessKey配置环境变量：
   ```
   export OSS_ACCESS_KEY_ID='YOUR_ACCESS_KEY_ID'
   export OSS_ACCESS_KEY_SECRET='YOUR_ACCESS_KEY_SECRET'
   ```
2. 执行以下命令检查环境变量是否生效：
   ```
   echo $OSS_ACCESS_KEY_ID
   echo $OSS_ACCESS_KEY_SECRET
   ```

## Windows

## CMD

```
setx OSS_ACCESS_KEY_ID "YOUR_ACCESS_KEY_ID"
setx OSS_ACCESS_KEY_SECRET "YOUR_ACCESS_KEY_SECRET"
```

## PowerShell

```
[Environment]::SetEnvironmentVariable("OSS_ACCESS_KEY_ID", "YOUR_ACCESS_KEY_ID", [EnvironmentVariableTarget]::User)
[Environment]::SetEnvironmentVariable("OSS_ACCESS_KEY_SECRET", "YOUR_ACCESS_KEY_SECRET", [EnvironmentVariableTarget]::User)
```

#### 代码示例

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;

public class OSSExample {
    public static void main(String[] args) {
        // 从环境变量中加载凭证信息，用于身份验证
        CredentialsProvider credentialsProvider = new EnvironmentVariableCredentialsProvider();
        
        // 创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region("<region-id>") // 填写Bucket所在地域
                .build()) {
            
            // 使用创建好的client执行后续操作...
            
        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
        }
    }
}
```

#### 静态凭证配置

以下示例代码展示了如何对访问凭据直接进行硬编码，显式设置要使用的访问密钥。

**警告**

请勿将访问凭据嵌入到生产环境的应用程序中，此方法仅用于测试目的。

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;

public class OSSExample {
    public static void main(String[] args) {
        // 创建静态凭证提供者，显式设置访问密钥
        // 请替换为您的RAM用户的AccessKey ID和AccessKey Secret
        CredentialsProvider credentialsProvider = new StaticCredentialsProvider(
                "YOUR_ACCESS_KEY_ID",
                "YOUR_ACCESS_KEY_SECRET"
        );
        
        // 创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region("<region-id>") // 填写Bucket所在地域
                .build()) {
            
            // 使用创建好的client执行后续操作...
            
        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
        }
    }
}
```

### 使用STS临时访问凭证

如果您的应用程序需要临时访问OSS，您可以使用通过STS服务获取的临时身份凭证（Access Key ID、Access Key Secret和Security Token）初始化凭证提供者。需要注意的是，该方式需要您手动维护一个STS Token，存在安全性风险和维护复杂度增加的风险。此外，如果您需要多次临时访问OSS，您需要手动刷新STS Token。

- 如果您希望通过OpenAPI的方式简单快速获取到STS临时访问凭证，请参见[AssumeRole - 获取扮演角色的临时身份凭证](https://help.aliyun.com/zh/ram/developer-reference/api-sts-2015-04-01-assumerole)。
- 如果您希望通过SDK的方式获取STS临时访问凭证，请参见[使用STS临时访问凭证访问OSS](https://help.aliyun.com/zh/oss/developer-reference/use-temporary-access-credentials-provided-by-sts-to-access-oss#section-rjh-18m-7kp)。
- 请注意，STS Token在生成的时候需要指定过期时间，过期后自动失效不能再使用。
- 如果您希望获取关于STS服务的接入点列表，请参见[服务接入点](https://help.aliyun.com/zh/ram/developer-reference/api-sts-2015-04-01-endpoint)。

#### 环境变量配置

**重要**

- 请注意，此处使用的是通过STS服务获取的临时身份凭证（Access Key ID、Access Key Secret和Security Token），而非RAM用户的AK。
- 请注意区分STS服务获取的Access Key ID以STS开头，例如"STS.L4aBSCSJVMuKg5U1\*\*\*\*"。

## Linux/macOS

```
export OSS_ACCESS_KEY_ID=<STS_ACCESS_KEY_ID>
export OSS_ACCESS_KEY_SECRET=<STS_ACCESS_KEY_SECRET>
export OSS_SESSION_TOKEN=<STS_SECURITY_TOKEN>
```

## Windows

```
set OSS_ACCESS_KEY_ID=<STS_ACCESS_KEY_ID>
set OSS_ACCESS_KEY_SECRET=<STS_ACCESS_KEY_SECRET>
set OSS_SESSION_TOKEN=<STS_SECURITY_TOKEN>
```

#### 代码示例

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;

public class OSSExample {
    public static void main(String[] args) {
        // 从环境变量中加载访问OSS所需的认证信息，用于身份验证
        CredentialsProvider credentialsProvider = new EnvironmentVariableCredentialsProvider();
        
        // 创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region("<region-id>") // 填写Bucket所在地域
                .build()) {
            
            // 使用创建好的client执行后续操作...
            
        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
        }
    }
}
```

#### 静态凭证配置

以下示例代码展示了如何对访问凭据直接进行硬编码，显式设置要使用的临时访问密钥。

**警告**

请勿将访问凭据嵌入到生产环境的应用程序中，此方法仅用于测试目的。

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;

public class OSSExample {
    public static void main(String[] args) {
        // 填写获取的临时访问密钥AccessKey ID和AccessKey Secret
        // 请注意区分STS服务获取的Access Key ID是以STS开头
        String stsAccessKeyId = "STS.****************";
        String stsAccessKeySecret = "yourAccessKeySecret";
        String stsSecurityToken = "yourSecurityToken";
        
        // 创建静态凭证提供者，显式设置临时访问密钥和STS安全令牌
        CredentialsProvider credentialsProvider = new StaticCredentialsProvider(
                stsAccessKeyId,
                stsAccessKeySecret,
                stsSecurityToken
        );
        
        // 创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region("<region-id>") // 填写Bucket所在地域
                .build()) {
            
            // 使用创建好的client执行后续操作...
            
        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
        }
    }
}
```

### 使用RAMRoleARN凭证

如果您的应用程序需要授权访问OSS，例如跨阿里云账号访问OSS，您可以使用RAMRoleARN初始化凭证提供者。该方式底层实现是STS Token。通过指定RAM角色的ARN（Alibabacloud Resource Name），凭证工具会前往STS服务获取STS Token，并在会话到期前调用AssumeRole接口申请新的STS Token。此外，您还可以通过为`policy`赋值来限制RAM角色到一个更小的权限集合。

**重要**

- 阿里云账号拥有资源的全部权限，AK一旦泄露，会给系统带来巨大风险，不建议使用。推荐使用最小化授权的RAM用户的AK。
- 如需创建RAM用户的AK，请直接访问[创建AccessKey](https://help.aliyun.com/zh/ram/user-guide/create-an-accesskey-pair#section-rjh-18m-7kp)。RAM用户的Access Key ID、Access Key Secret信息仅在创建时显示，请及时保存，如若遗忘请考虑创建新的AK进行轮换。
- 如需获取RAMRoleARN，请直接访问[创建角色](https://help.aliyun.com/zh/ram/developer-reference/api-ram-2015-05-01-createrole)。

#### 添加依赖

在您的`pom.xml`中添加阿里云凭证管理依赖：

```
<dependency>
    <groupId>com.aliyun</groupId>
    <artifactId>credentials-java</artifactId>
    <version>0.3.4</version>
</dependency>
```

#### 配置AK和RAMRoleARN作为访问凭证

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.Credentials;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProviderSupplier;
// 注意：以下import来自外部依赖 credentials-java
import com.aliyun.credentials.Client;
import com.aliyun.credentials.models.Config;

public class OSSExample {
    public static void main(String[] args) {
        // 配置RAMRoleARN凭证
        Config credentialConfig = new Config()
                .setType("ram_role_arn")
                // 从环境变量中获取RAM用户的访问密钥（AccessKey ID和AccessKey Secret）
                .setAccessKeyId(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID"))
                .setAccessKeySecret(System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET"))
                // 要扮演的RAM角色ARN，示例值：acs:ram::123456789012****:role/adminrole
                // 可以通过环境变量ALIBABA_CLOUD_ROLE_ARN设置RoleArn
                .setRoleArn("acs:ram::123456789012****:role/adminrole")
                // 角色会话名称，可以通过环境变量ALIBABA_CLOUD_ROLE_SESSION_NAME设置RoleSessionName
                .setRoleSessionName("your-session-name")
                // 设置更小的权限策略，非必填。示例值：{"Statement": [{"Action": ["*"],"Effect": "Allow","Resource": ["*"]}],"Version":"1"}
                .setPolicy("{\"Statement\": [{\"Action\": [\"*\"],\"Effect\": \"Allow\",\"Resource\": [\"*\"]}],\"Version\":\"1\"}")
                // 设置角色会话有效期，单位为秒，默认值为3600秒（1小时），非必填
                .setRoleSessionExpiration(3600);

        Client credentialClient = new Client(credentialConfig);

        // 创建凭证提供者，用于动态加载凭证
        CredentialsProvider credentialsProvider = new CredentialsProviderSupplier(() -> {
            try {
                com.aliyun.credentials.models.CredentialModel cred = credentialClient.getCredential();
                return new Credentials(
                    cred.getAccessKeyId(),
                    cred.getAccessKeySecret(),
                    cred.getSecurityToken()
                );
            } catch (Exception e) {
                throw new RuntimeException("获取凭证失败", e);
            }
        });

        // 创建OSS客户端实例
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region("<region-id>") // 填写Bucket所在地域
                .build()) {
            
            // 使用client进行后续操作...
            
        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
        }
    }
}
```

### 使用ECSRAMRole凭证

如果您的应用程序运行在ECS实例、ECI实例、容器服务Kubernetes版的Worker节点中，建议您使用ECSRAMRole初始化凭证提供者。该方式底层实现是STS Token。ECSRAMRole允许您将一个角色关联到ECS实例、ECI实例或容器服务 Kubernetes 版的Worker节点，实现在实例内部自动刷新STS Token。该方式无需您提供一个AK或STS Token，消除了手动维护AK或STS Token的风险。如何获取ECSRAMRole，请参见[创建角色](https://help.aliyun.com/zh/ram/developer-reference/api-ram-2015-05-01-createrole)。

#### 添加依赖

```
<dependency>
    <groupId>com.aliyun</groupId>
    <artifactId>credentials-java</artifactId>
    <version>0.3.4</version>
</dependency>
```

#### 配置ECSRAMRole作为访问凭证

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.Credentials;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProviderSupplier;
// 注意：以下import来自外部依赖 credentials-java
import com.aliyun.credentials.Client;
import com.aliyun.credentials.models.Config;

public class OSSExample {
    public static void main(String[] args) {
        // 配置ECSRAMRole凭证
        Config credentialConfig = new Config()
                .setType("ecs_ram_role")      // 访问凭证类型。固定为ecs_ram_role
                .setRoleName("EcsRoleExample"); // 为ECS授予的RAM角色的名称。可选参数。如果不设置，将自动检索。强烈建议设置，以减少请求

        Client credentialClient = new Client(credentialConfig);

        // 创建凭证提供者，用于动态加载凭证
        CredentialsProvider credentialsProvider = new CredentialsProviderSupplier(() -> {
            try {
                com.aliyun.credentials.models.CredentialModel cred = credentialClient.getCredential();
                return new Credentials(
                    cred.getAccessKeyId(),
                    cred.getAccessKeySecret(),
                    cred.getSecurityToken()
                );
            } catch (Exception e) {
                throw new RuntimeException("获取凭证失败", e);
            }
        });

        // 创建OSS客户端实例
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region("<region-id>") // 填写Bucket所在地域
                .build()) {
            
            // 使用client进行后续操作...
            
        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
        }
    }
}
```

### 使用OIDCRoleARN凭证

在容器服务Kubernetes版中设置了Worker节点RAM角色后，对应节点内的Pod中的应用也就可以像ECS上部署的应用一样，通过元数据服务（Meta Data Server）获取关联角色的STS Token。但如果容器集群上部署的是不可信的应用（比如部署您的客户提交的应用，代码也没有对您开放），您可能并不希望它们能通过元数据服务获取Worker节点关联实例RAM角色的STS Token。为了避免影响云上资源的安全，同时又能让这些不可信的应用安全地获取所需的STS Token，实现应用级别的权限最小化，您可以使用RRSA（RAM Roles for Service Account）功能。该方式底层实现是STS Token。阿里云容器集群会为不同的应用Pod创建和挂载相应的服务账户OIDC Token文件，并将相关配置信息注入到环境变量中，凭证工具通过获取环境变量的配置信息，调用STS服务的AssumeRoleWithOIDC接口换取绑定角色的STS Token。该方式无需您提供一个AK或STS Token，消除了手动维护AK或STS Token的风险。详情请参见[通过RRSA配置ServiceAccount的RAM权限实现Pod权限隔离](https://help.aliyun.com/zh/ack/ack-managed-and-ack-dedicated/user-guide/use-rrsa-to-authorize-pods-to-access-different-cloud-services#task-2142941)。

#### 添加依赖

```
<dependency>
    <groupId>com.aliyun</groupId>
    <artifactId>credentials-java</artifactId>
    <version>0.3.4</version>
</dependency>
```

#### 配置OIDCRoleARN作为访问凭证

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.Credentials;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProviderSupplier;
// 注意：以下import来自外部依赖 credentials-java
import com.aliyun.credentials.Client;
import com.aliyun.credentials.models.Config;

public class OSSExample {
    public static void main(String[] args) {
        // 配置OIDCRoleARN凭证
        Config credentialConfig = new Config()
                // 指定Credential类型，固定值为oidc_role_arn
                .setType("oidc_role_arn")
                // RAM角色名称ARN，可以通过环境变量ALIBABA_CLOUD_ROLE_ARN设置RoleArn
                .setRoleArn(System.getenv("ALIBABA_CLOUD_ROLE_ARN"))
                // OIDC提供商ARN，可以通过环境变量ALIBABA_CLOUD_OIDC_PROVIDER_ARN设置OidcProviderArn
                .setOidcProviderArn(System.getenv("ALIBABA_CLOUD_OIDC_PROVIDER_ARN"))
                // OIDC Token文件路径，可以通过环境变量ALIBABA_CLOUD_OIDC_TOKEN_FILE设置OidcTokenFilePath
                .setOidcTokenFilePath(System.getenv("ALIBABA_CLOUD_OIDC_TOKEN_FILE"))
                // 角色会话名称，可以通过环境变量ALIBABA_CLOUD_ROLE_SESSION_NAME设置RoleSessionName
                .setRoleSessionName("your-session-name")
                // 设置更小的权限策略，非必填。示例值：{"Statement": [{"Action": ["*"],"Effect": "Allow","Resource": ["*"]}],"Version":"1"}
                .setPolicy("{\"Statement\": [{\"Action\": [\"*\"],\"Effect\": \"Allow\",\"Resource\": [\"*\"]}],\"Version\":\"1\"}")
                // 设置角色会话有效期，单位为秒，默认值为3600秒（1小时），非必填
                .setRoleSessionExpiration(3600);

        Client credentialClient = new Client(credentialConfig);

        // 创建凭证提供者，用于动态加载凭证
        CredentialsProvider credentialsProvider = new CredentialsProviderSupplier(() -> {
            try {
                com.aliyun.credentials.models.CredentialModel cred = credentialClient.getCredential();
                return new Credentials(
                    cred.getAccessKeyId(),
                    cred.getAccessKeySecret(),
                    cred.getSecurityToken()
                );
            } catch (Exception e) {
                throw new RuntimeException("获取凭证失败", e);
            }
        });

        // 创建OSS客户端实例
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region("<region-id>") // 填写Bucket所在地域
                .build()) {
            
            // 使用client进行后续操作...
            
        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
        }
    }
}
```

### 使用自定义访问凭证

当以上凭证配置方式不满足要求时，您可以自定义获取凭证的方式。Java SDK支持多种实现方式。

#### 通过Supplier接口实现

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.Credentials;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProviderSupplier;

public class OSSExample {
    public static void main(String[] args) {
        // 创建自定义凭证提供者
        CredentialsProvider credentialsProvider = new CredentialsProviderSupplier(() -> {
            // TODO: 实现您的自定义凭证获取逻辑
            
            // 返回长期凭证
            return new Credentials("access_key_id", "access_key_secret");
            
            // 返回STS临时凭证（如果需要）
            // return new Credentials("sts_access_key_id", "sts_access_key_secret", "security_token");
        });
        
        // 创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region("<region-id>") // 填写Bucket所在地域
                .build()) {
            
            // 使用创建好的client执行后续操作...
            
        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
        }
    }
}
```

#### 实现CredentialsProvider接口

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.Credentials;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;

public class CustomCredentialsProvider implements CredentialsProvider {
    
    @Override
    public Credentials getCredentials() {
        // TODO: 实现您的自定义凭证获取逻辑
        
        // 返回长期凭证
        return new Credentials("access_key_id", "access_key_secret");
        
        // 返回STS临时凭证（如果需要）
        // 对于临时凭证，需要根据过期时间，刷新凭证
        // return new Credentials("sts_access_key_id", "sts_access_key_secret", "security_token");
    }
}

public class OSSExample {
    public static void main(String[] args) {
        // 创建自定义凭证提供者
        CredentialsProvider credentialsProvider = new CustomCredentialsProvider();
        
        // 创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region("<region-id>") // 填写Bucket所在地域
                .build()) {
            
            // 使用创建好的client执行后续操作...
            
        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
        }
    }
}
```

### 匿名访问

如果您只需要访问公共读取权限的OSS资源，可以使用匿名访问方式，无需提供任何凭证。

> 运行示例代码前，请将代码中的 `<region-id>`替换为实际的[地域和Endpoint](https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints)，如 `cn-hangzhou`。

```
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.AnonymousCredentialsProvider;

public class OSSExample {
    public static void main(String[] args) {
        // 创建匿名凭证提供者
        CredentialsProvider credentialsProvider = new AnonymousCredentialsProvider();
        
        // 创建OSS客户端
        try (OSSClient client = OSSClient.newBuilder()
                .credentialsProvider(credentialsProvider)
                .region("<region-id>") // 填写Bucket所在地域
                .build()) {
            
            // 使用创建好的client执行后续操作...
            // 注意：匿名访问只能访问具有公共读取权限的资源
            
        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
        }
    }
}
```

## 示例代码

| **功能分类**  | **示例说明**                                                                                                                                                        | **同步版本**                                                                                                                                                                  | **异步版本**                                                                                                                                                              |
| :-------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **存储空间**  | 创建存储空间                                                                                                                                                          | [PutBucket.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutBucket.java)                                   | [PutBucketAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutBucketAsync.java)                     |
| 列举存储空间    | [ListBuckets.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/ListBuckets.java)                     | [ListBucketsAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/ListBucketsAsync.java)                     | <br />                                                                                                                                                                |
| 获取存储空间信息  | [GetBucketInfo.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetBucketInfo.java)                 | [GetBucketInfoAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetBucketInfoAsync.java)                 | <br />                                                                                                                                                                |
| 获取存储空间地域  | [GetBucketLocation.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetBucketLocation.java)         | [GetBucketLocationAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetBucketLocationAsync.java)         | <br />                                                                                                                                                                |
| 获取存储容量统计  | [GetBucketStat.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetBucketStat.java)                 | [GetBucketStatAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetBucketStatAsync.java)                 | <br />                                                                                                                                                                |
| 删除存储空间    | [DeleteBucket.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/DeleteBucket.java)                   | [DeleteBucketAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/DeleteBucketAsync.java)                   | <br />                                                                                                                                                                |
| **文件上传**  | 简单上传                                                                                                                                                            | [PutObject.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutObject.java)                                   | [PutObjectAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutObjectAsync.java)                     |
| 追加上传      | [AppendObject.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/AppendObject.java)                   | [AppendObjectAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/AppendObjectAsync.java)                   | <br />                                                                                                                                                                |
| 分片上传      | [MultipartUpload.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/MultipartUpload.java)             | [MultipartUploadAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/MultipartUploadAsync.java)             | <br />                                                                                                                                                                |
| 列举分片上传任务  | [ListMultipartUploads.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/ListMultipartUploads.java)   | [ListMultipartUploadsAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/ListMultipartUploadsAsync.java)   | <br />                                                                                                                                                                |
| 列举已上传分片   | [ListParts.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/ListParts.java)                         | [ListPartsAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/ListPartsAsync.java)                         | <br />                                                                                                                                                                |
| 取消分片上传    | [AbortMultipartUpload.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/AbortMultipartUpload.java)   | [AbortMultipartUploadAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/AbortMultipartUploadAsync.java)   | <br />                                                                                                                                                                |
| **文件下载**  | 简单下载                                                                                                                                                            | [GetObject.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetObject.java)                                   | [GetObjectAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetObjectAsync.java)                     |
| **文件管理**  | 拷贝文件                                                                                                                                                            | [CopyObject.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/CopyObject.java)                                 | [CopyObjectAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/CopyObjectAsync.java)                   |
| 判断文件是否存在  | [HeadObject.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/HeadObject.java)                       | [HeadObjectAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/HeadObjectAsync.java)                       | <br />                                                                                                                                                                |
| 列举文件      | [ListObjects.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/ListObjects.java)                     | [ListObjectsAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/ListObjectsAsync.java)                     | <br />                                                                                                                                                                |
| 列举文件V2    | [ListObjectsV2.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/ListObjectsV2.java)                 | [ListObjectsV2Async.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/ListObjectsV2Async.java)                 | <br />                                                                                                                                                                |
| 删除文件      | [DeleteObject.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/DeleteObject.java)                   | [DeleteObjectAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/DeleteObjectAsync.java)                   | <br />                                                                                                                                                                |
| 批量删除文件    | [DeleteMultipleObjects.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/DeleteMultipleObjects.java) | [DeleteMultipleObjectsAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/DeleteMultipleObjectsAsync.java) | <br />                                                                                                                                                                |
| 获取文件元数据   | [GetObjectMeta.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetObjectMeta.java)                 | [GetObjectMetaAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetObjectMetaAsync.java)                 | <br />                                                                                                                                                                |
| **归档文件**  | 解冻文件                                                                                                                                                            | [RestoreObject.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/RestoreObject.java)                           | [RestoreObjectAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/RestoreObjectAsync.java)             |
| 清理已解冻文件   | [CleanRestoredObject.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/CleanRestoredObject.java)     | [CleanRestoredObjectAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/CleanRestoredObjectAsync.java)     | <br />                                                                                                                                                                |
| **软链接**   | 创建软链接                                                                                                                                                           | [PutSymlink.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutSymlink.java)                                 | [PutSymlinkAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutSymlinkAsync.java)                   |
| 获取软链接     | [GetSymlink.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetSymlink.java)                       | [GetSymlinkAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetSymlinkAsync.java)                       | <br />                                                                                                                                                                |
| **对象标签**  | 设置对象标签                                                                                                                                                          | [PutObjectTagging.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutObjectTagging.java)                     | [PutObjectTaggingAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutObjectTaggingAsync.java)       |
| 获取对象标签    | [GetObjectTagging.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetObjectTagging.java)           | [GetObjectTaggingAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetObjectTaggingAsync.java)           | <br />                                                                                                                                                                |
| 删除对象标签    | [DeleteObjectTagging.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/DeleteObjectTagging.java)     | [DeleteObjectTaggingAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/DeleteObjectTaggingAsync.java)     | <br />                                                                                                                                                                |
| **访问控制**  | 设置存储空间ACL                                                                                                                                                       | [PutBucketAcl.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutBucketAcl.java)                             | [PutBucketAclAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutBucketAclAsync.java)               |
| 获取存储空间ACL | [GetBucketAcl.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetBucketAcl.java)                   | [GetBucketAclAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetBucketAclAsync.java)                   | <br />                                                                                                                                                                |
| 设置文件ACL   | [PutObjectAcl.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutObjectAcl.java)                   | [PutObjectAclAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutObjectAclAsync.java)                   | <br />                                                                                                                                                                |
| 获取文件ACL   | [GetObjectAcl.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetObjectAcl.java)                   | [GetObjectAclAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetObjectAclAsync.java)                   | <br />                                                                                                                                                                |
| **版本控制**  | 设置版本控制                                                                                                                                                          | [PutBucketVersioning.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutBucketVersioning.java)               | [PutBucketVersioningAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutBucketVersioningAsync.java) |
| 获取版本控制状态  | [GetBucketVersioning.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetBucketVersioning.java)     | [GetBucketVersioningAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetBucketVersioningAsync.java)     | <br />                                                                                                                                                                |
| 列举文件版本    | [ListObjectVersions.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/ListObjectVersions.java)       | [ListObjectVersionsAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/ListObjectVersionsAsync.java)       | <br />                                                                                                                                                                |
| **跨域访问**  | 设置CORS规则                                                                                                                                                        | [PutBucketCors.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutBucketCors.java)                           | [PutBucketCorsAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/PutBucketCorsAsync.java)             |
| 获取CORS规则  | [GetBucketCors.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetBucketCors.java)                 | [GetBucketCorsAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/GetBucketCorsAsync.java)                 | <br />                                                                                                                                                                |
| 删除CORS规则  | [DeleteBucketCors.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/DeleteBucketCors.java)           | [DeleteBucketCorsAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/DeleteBucketCorsAsync.java)           | <br />                                                                                                                                                                |
| 跨域预检请求    | [OptionObject.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/OptionObject.java)                   | [OptionObjectAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/OptionObjectAsync.java)                   | <br />                                                                                                                                                                |
| **系统功能**  | 查询Endpoint信息                                                                                                                                                    | [DescribeRegions.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/DescribeRegions.java)                       | [DescribeRegionsAsync.java](https://github.com/aliyun/alibabacloud-oss-java-sdk-v2/blob/main/samples/src/main/java/com/example/oss/DescribeRegionsAsync.java)         |

