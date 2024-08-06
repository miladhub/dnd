```bash
curl localhost:8080/updates/42
```

```bash
curl localhost:8080/updates/42 -F description='foo'
```

```bash
curl -v localhost:8080/updates/42 -F action='Attack' -F info='wolf'
```

```bash
export OPENAI_API_KEY=...
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
  -jar target/quarkus-app/quarkus-run.jar
```

<http://localhost:8080/>