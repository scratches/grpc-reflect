## Spring Boot gRPC Migration Guide: 4.0.x → 4.1.0

### 1. Remove the Spring gRPC BOM

In 4.0.x, projects imported the Spring gRPC dependency BOM explicitly. In 4.1.0 this is managed by the Spring Boot parent POM and the `<dependencyManagement>` block can be removed entirely.

**Before** (all samples):
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.grpc</groupId>
            <artifactId>spring-grpc-dependencies</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```
**After**: delete the entire `<dependencyManagement>` block.

---

### 2. Replace Spring gRPC starters with Spring Boot starters

The gRPC starters have been promoted into Spring Boot itself and renamed. Update your `<dependencies>` accordingly.

| 4.0.x artifact | 4.1.0 artifact |
|---|---|
| `org.springframework.grpc:spring-grpc-spring-boot-starter` | `org.springframework.boot:spring-boot-starter-grpc-server` |
| `org.springframework.grpc:spring-grpc-client-spring-boot-starter` | `org.springframework.boot:spring-boot-starter-grpc-client` |
| `org.springframework.grpc:spring-grpc-test` | `org.springframework.boot:spring-boot-starter-grpc-client-test` (or `spring-boot-starter-test`) |

References: pom.xml, pom.xml, pom.xml, pom.xml.

---

### 3. Rename the gRPC client channel address property

The configuration key for pointing a client at a server has changed.

**Before** (all test configurations):
```properties
spring.grpc.client.default-channel.address=0.0.0.0:${local.grpc.port}
```
**After**:
```properties
spring.grpc.client.channel.default.target=0.0.0.0:${local.grpc.server.port}
```

Note also that the placeholder for the server port changed from `${local.grpc.port}` to `${local.grpc.server.port}` in the pure gRPC server case.

References: samples/app/src/test/java/…/GrpcServerApplicationTests.java, samples/bindable/src/test/java/…/GrpcReactorServerApplicationTests.java, samples/web/src/test/java/…/GrpcServerApplicationTests.java, samples/webflux/src/test/java/…/GrpcServerApplicationTests.java.

---

### 4. Explicitly register gRPC stubs in tests with `@ImportGrpcClients`

Tests that `@Autowired` a generated gRPC stub must now declare an inner `@TestConfiguration` annotated with `@ImportGrpcClients` to register the stub type(s) in the application context. Previously this was done automatically.

**Add to test classes** (bindable, web, webflux):
```java
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.grpc.client.ImportGrpcClients;

// ...inside the test class:
@TestConfiguration
@ImportGrpcClients(types = SimpleGrpc.SimpleBlockingStub.class)
static class ExtraConfiguration {}
```

References: samples/bindable/src/test/java/…/GrpcServerApplicationTests.java, samples/web/src/test/java/…/GrpcServerApplicationTests.java, samples/webflux/src/test/java/…/GrpcServerApplicationTests.java.

---

### 5. Update `protobuf-maven-plugin` configuration

The `io.github.ascopes:protobuf-maven-plugin` has a new configuration schema. Version management and explicit `<executions>` are now provided by the Spring Boot parent, so most boilerplate can be removed.

- **Remove** the `<version>` element, `<protocVersion>`, and the explicit `<executions>` block with the `generate` goal.
- **Rename** the plugin element containers: `<binaryMavenPlugins>/<binaryMavenPlugin>` → `<plugins>/<plugin kind="binary-maven">` and `<jvmMavenPlugins>/<jvmMavenPlugin>` → `<plugins>/<plugin kind="jvm-maven">`.
- **Remove** the `jakarta_omit` option from `protoc-gen-grpc-java` options.
- **Use** the managed `${grpc-java.version}` property instead of `${grpc.version}` for the `protoc-gen-grpc-java` plugin version.
- For projects without reactive stubs (web, webflux), the entire `<configuration>` block can be deleted and the plugin entry reduced to just the `<groupId>` and `<artifactId>`.

Reference: pom.xml, pom.xml, pom.xml.

---

### 6. Pin `reactor-grpc-stub` version

`com.salesforce.servicelibs:reactor-grpc-stub` is no longer version-managed by the Spring Boot parent and requires an explicit version:

```xml
<dependency>
    <groupId>com.salesforce.servicelibs</groupId>
    <artifactId>reactor-grpc-stub</artifactId>
    <version>1.2.4</version>
</dependency>
```

Reference: pom.xml.

---

### 7. Upgrade gRPC version (if overriding)

Projects that override the managed gRPC version should update from `1.77.0` to `1.81.0`:

```xml
<grpc.version>1.81.0</grpc.version>
```

Reference: pom.xml, pom.xml.