DnD™-like app using ChatGPT as the Dungeon Master
===

*Disclaimer* - This is a hobby-time pet project, run it at your own risk.

# Pre-requisites

* [JDK 21](https://www.oracle.com/java/technologies/downloads/)
* [OpenAI](http://openai.com) key

# Building

Building requires [Maven](https://maven.apache.org):

```shell
mvn clean install
```

# Running on a server

To install and run the game on a server, expand the contents of file
`target/dnd-*-dist.zip` to a folder of your choice - e.g., `~/dnd`:

```shell
unzip target/dnd-*-dist.zip -d ~
mv ~/dnd-*-SNAPSHOT ~/dnd
```

To start the game, issue:

```shell
cd ~/dnd
export OPENAI_API_KEY=...
java -jar quarkus-run.jar 
```

The game is accessible at <http://localhost:8080/>.

# Running in dev mode

This will re-build the project, run the tests and execute the game engine:

```shell
export OPENAI_API_KEY=...
mvn quarkus:dev
```

The game server will listen on port `8080` and the debug port is `5005`.

Note that this will listen only on `localhost`. To listen on all IPs, add
`-Dquarkus.http.host=0.0.0.0` the the above command, see
<https://quarkus.io/guides/all-config#quarkus-vertx-http_quarkus.http.host>.

# Running in production mode

```shell
export OPENAI_API_KEY=...
java -jar target/quarkus-app/quarkus-run.jar
```

The game server will listen on port `8080` on all IPs (i.e., on `0.0.0.0`).

This runs it in debug:

```bash
export OPENAI_API_KEY=...
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
  -jar target/quarkus-app/quarkus-run.jar
```