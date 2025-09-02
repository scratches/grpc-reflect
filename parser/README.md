A [Protobuf](https://github.com/protocolbuffers/protobuf) parser generated from an [Antlr](https://github.com/antlr/antlr4) grammar. Originally based on the sample grammar at https://github.com/antlr/grammars-v4.

Usage:

```java
FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
FileDescriptorSet proto = parser.resolve(Paths.of("path/to/file.proto"));
```

Supports parsing of proto files in "proto3" syntax, including imports and package declarations. The parser can resolve dependencies from the classpath or relative to the base path. Message types, enums, and services are parsed, and the resulting structure can be used for further processing or analysis. Options and extensions are dropped (but available in the parser if anyone needs them).

Manual testing example for simple proto file defining a single message with a string field:

```bash
$ mvn compile
$ ./grun.jsh com.example.Protobuf proto -tree 
syntax = "proto3";
message TestMessage {
        string value = 1;
}
^D
(proto (syntax syntax = "proto3" ;) (topLevelDef (messageDef message (messageName (ident TestMessage)) (messageBody { (messageElement (field (type string) (fieldName (ident value)) = (fieldNumber (intLit 1)) ;)) }))) <EOF>)
```