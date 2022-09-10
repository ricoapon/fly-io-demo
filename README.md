# fly-io-demo

This project shows you how easy it is to deploy your usual Spring Boot project to [Fly.io](https://fly.io/). This
example has been created, because Heroku is (removing their free tier)[https://blog.heroku.com/next-chapter].

# Migration

## Step 0: Create a Spring Boot project

I assume you already have a project. If not, you can easily create your own project using
[spring initializr](https://start.spring.io/). For this project, I will assume you have the following:

* Gradle as build tool (same can easily be done with Maven, but examples will be with Gradle commands)
* PostgreSQL as database (this is required, since Fly.io only supports PostgreSQL for free)
* Runnable JAR with embedded tomcat (makes Dockerfile easy)

In this project I have configured Liquibase, to automatically configure the database. This ensures I can use H2
in-memory database for running tests. I also use Java 17, but it works for any Java version.

## Step 1: Prepare Fly.io

If you haven't registered an account yet, do so.

Make sure to have `flyctl` installed (
see [https://fly.io/docs/hands-on/install-flyctl/](https://fly.io/docs/hands-on/install-flyctl/)).

Ensure that you are signed in as well ([https://fly.io/docs/hands-on/sign-in/](https://fly.io/docs/hands-on/sign-in/)).
This comes down to executing the following command:

```
flyctl auth login
```

## Step 1: Create Dockerfile

We are going to work with the most simple Dockerfile you can imagine:

```
FROM openjdk:17-alpine
COPY build/libs/fly-io-demo-*.jar application.jar
CMD ["java","-jar","application.jar"]
```

You obviously have to replace `fly-io-demo` with the name of your project.

If you are using something else than Java 17, find your own Docker image with your Java version.

## Step 2: Create Fly.io application

Create your application by executing the following command:

```
flyctl launch
```

A few questions will be asked. The name and region can be whatever you like (I used the suggested defaults). When asked
for a PostgreSQL database: answer yes. A new app will be automatically created and linked to this current app. It will
give you a long list of information. Gather the following information for the next steps:

* Username (I assume this is `postgres`)
* Password
* Database URL

After the command is finished a new file `fly.toml` is created. This will contain the configuration you filled in.

## Step 3: Configure Fly.io PostgreSQL in your application

Change your Spring Boot `application.properties` to connect to the database as follows:

```
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=postgres
spring.datasource.password=${DATABASE_PASSWORD}
```

The given database URL by Fly.io is not suitable for our JDBC connection URL. You have to change this. My URL looked
like this:

```
postgres://weathered_sky_5211:<random characters>@top2.nearest.of.weathered-sky-5211-db.internal:5432/weathered_sky_5211
```

Note that it starts with `postgres` and not `postgresql`! It took my quite some time to see this... Transform this URL
to look as follows:

```
jdbc:postgresql://weathered-sky-5211-db.internal:5432/weathered_sky_5211
```

Configure the secrets for your application:

```
flyctl secrets set DATABSE_URL=<your URL>
flyctl secrets set DATABASE_PASSWORD=<your password>
```

## Step 3: Prepare database

If you are using Liquibase or Flyway or another tool for automatic migrations: you can skip this step.

Your database probably needs some tables and optionally some inserted data. If you want to do this manually, you can do
so as follows.

First create a connection with the database:

```
flyctl proxy 5432 -a <name of your app>-db
```

You can now connect to your database on localhost with port 5432. I use IntelliJ and connected with my database with the
following connection URL:

```
jdbc:postgresql://localhost:5432/weathered_sky_5211
```

Note: you have to fill in the username and password that you got earlier for the connection!

Finally, you can execute your manual actions on the database.

## Step 4: Launch

Everything is now ready to actually launch your application! Note that the Dockerfile uses a built JAR file. To ensure
that we really use the latest version, we first run the clean command.

```
./gradlew clean bootJar
flyctl deploy
```

I hope it all worked out well and your application deployed as expected!

## Step 5: GitHub Actions

Of course, nobody wants to do this manually everytime. So we create some simple GitHub Actions to automatically build
and deploy for us.

First run the following command:

```
flyctl auth token
```

This will give you the API token that we need for our GitHub Action. Configure a secret with the name `FLY_API_TOKEN`
and the value the response of the previous command.

Now add a new workflow with the following content:

```
name: Deploy to Fly.io
on:
  push:
    branches:
      - master
jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v2

      # This can be changed depending on your Java version.
      - uses: actions/setup-java@v3
        with:
          distribution: temurin
          java-version: 17

      # If you want to use `build` instead so tests are run as well, see "Plain Jar" paragraph below.
      - name: Build project
        run: ./gradlew bootJar

      - name: Setup flyctl
        uses: superfly/flyctl-actions/setup-flyctl@master

      - run: flyctl deploy --remote-only
        env:
          FLY_API_TOKEN: ${{ secrets.FLY_API_TOKEN }}
```

This workflow will now deploy your code to Fly.io whenever you change your code on the master branch! Note: you might
not want to deploy that often. Maybe only do this when you are tagging or releasing your application.

## Step 6: (Optional) Use your own custom domain

Fly.io (and other sites) create ugly URLs for your web application like `weathered-sky-5211.fly.dev`. I am a big fan of
using my own custom domain for my sites. This was very simple for the Fly.io application.

The steps are written in this
blog: [https://fly.io/blog/how-to-custom-domains-with-fly/](https://fly.io/blog/how-to-custom-domains-with-fly/). I will
copy-paste them here just in case:

* Run `flyctl ips list -a <name of your app>` to get the IPv4 and IPv6 addresses.
* Head over to your DNS provider and add A and AAAA records for `example.com` with the IPv4 and IPv6 values.
* Run `flyctl certs create -a <name of your app> example.com`
* Run `flyctl certs show -a <name of your app>` to watch your certificates being issued.
* Connect to `https://example.com` and use your application with auto-renewing Let's Encrypt TLS certificates, edge TLS,
  HTTP/2 support and more.

# Additional notes

## Plain jar

The Dockerfile is very simple: it finds the jar in the `build/libs` directory and uses this to run your application.
However, Spring Boot automatically creates other jar with the suffix `-plain.jar`. This jar is created when
running `./gradlew build` but not when running `./gradlew bootJar`. To avoid the plain jar to be created at all, add the
following to your Gradle configuration:

```
tasks.getByName<Jar>("jar") {
    enabled = false
}
```

Now you can change your command `bootJar` to `build` if you want.

## Multi-stage Dockerfile

We are now building our code with Gradle beforehand, and then include the build output in the Dockerfile. You can also
make a Dockerfile that first builds the code with Gradle (inside the Dockerfile) and then creates a separate image with
the output of the build.

Whatever is needed for your project, go add it! This project contains a small example to get your application up and
running in a very simple way.
