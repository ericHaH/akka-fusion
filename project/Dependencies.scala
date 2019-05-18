import sbt._

object Dependencies {
  val versionScala           = "2.12.8"
  val versionScalaLib        = "2.12"
  val versionScalaXml        = "1.2.0"
  val versionAkka            = "2.5.22"
  val versionAkkaManagement  = "1.0.0"
  val versionAkkaHttp        = "10.1.8"
  val versionAlpakka         = "1.0.0"
  val versionAkkaStreamKafka = "1.0.2"
  val versionCirce           = "0.11.1"
  val versionKamon           = "1.1.3"
  val versionJackson         = "2.9.8"
  val versionSlick           = "3.3.0"
  val versionPoi             = "4.1.0"
  val versionDoobie          = "0.6.0"
  val versionQuartz          = "2.2.3"
  val versionScalameta       = "4.1.6"
  val versionScalatest = "3.0.7"

  val _scalameta = "org.scalameta" %% "scalameta" % versionScalameta

  val _scalaXml = ("org.scala-lang.modules" %% "scala-xml" % versionScalaXml).exclude("org.scala-lang", "scala-library")

  val _scalaJava8Compat =
    ("org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0").exclude("org.scala-lang", "scala-library")

  val _fastparse = "com.lihaoyi" %% "fastparse" % "2.1.0"

  val _scalatest = "org.scalatest" %% "scalatest" % versionScalatest

  val _akkaRemote        = "com.typesafe.akka" %% "akka-remote"         % versionAkka
  val _akkaStream        = "com.typesafe.akka" %% "akka-stream"         % versionAkka
  val _akkaTestkit       = "com.typesafe.akka" %% "akka-testkit"        % versionAkka
  val _akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % versionAkka

  val _akkas =
    Seq("com.typesafe.akka" %% "akka-slf4j" % versionAkka, _akkaStream, _akkaTestkit % Test, _akkaStreamTestkit % Test)
      .map(_.exclude("org.scala-lang.modules", s"scala-java8-compat").cross(CrossVersion.binary))
  val _akkaPersistence      = "com.typesafe.akka" %% "akka-persistence-query"  % versionAkka
  val _akkaMultiNodeTestkit = "com.typesafe.akka" %% "akka-multi-node-testkit" % versionAkka % Test

  val _akkaClusters = Seq(
    "com.typesafe.akka" %% "akka-cluster"                % versionAkka,
    "com.typesafe.akka" %% "akka-cluster-typed"          % versionAkka,
    "com.typesafe.akka" %% "akka-cluster-tools"          % versionAkka,
    "com.typesafe.akka" %% "akka-cluster-metrics"        % versionAkka,
    "com.typesafe.akka" %% "akka-cluster-sharding"       % versionAkka,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % versionAkka,
    _akkaMultiNodeTestkit)

  val _akkaManagement =
    ("com.lightbend.akka.management" %% "akka-management" % versionAkkaManagement)
      .excludeAll(ExclusionRule("com.typesafe.akka"))
      .exclude("org.scala-lang", "scala-library")

  val _akkaManagementClusterHttp =
    ("com.lightbend.akka.management" %% "akka-management-cluster-http" % versionAkkaManagement)
      .excludeAll(ExclusionRule("com.typesafe.akka"))
      .exclude("org.scala-lang", "scala-library")
  val _akkaHttpCore    = "com.typesafe.akka" %% "akka-http-core"    % versionAkkaHttp
  val _akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % versionAkkaHttp
  val _akkaHttpCors    = "ch.megard"         %% "akka-http-cors"    % "0.4.0"

  val _akkaHttps =
    Seq("com.typesafe.akka" %% "akka-http" % versionAkkaHttp, _akkaHttpCors, _akkaHttpTestkit % Test).map(
      _.exclude("com.typesafe.akka", "akka-stream")
        .withCrossVersion(CrossVersion.binary)
        .exclude("com.typesafe.akka", "akka-stream-testkit")
        .withCrossVersion(CrossVersion.binary))

  val _alpakkaSimpleCodecs =
    ("com.lightbend.akka" %% "akka-stream-alpakka-simple-codecs" % versionAlpakka)
      .excludeAll(ExclusionRule("com.typesafe.akka"))

  val _alpakkaXml =
    ("com.lightbend.akka" %% "akka-stream-alpakka-xml" % versionAlpakka).excludeAll(ExclusionRule("com.typesafe.akka"))

  val _alpakkaCsv =
    ("com.lightbend.akka" %% "akka-stream-alpakka-csv" % versionAlpakka).excludeAll(ExclusionRule("com.typesafe.akka"))

  val _alpakkaJsonStreaming =
    ("com.lightbend.akka" %% "akka-stream-alpakka-json-streaming" % versionAlpakka)
      .excludeAll(ExclusionRule("com.typesafe.akka"))

  val _alpakkaFile =
    ("com.lightbend.akka" %% "akka-stream-alpakka-file" % versionAlpakka).excludeAll(ExclusionRule("com.typesafe.akka"))

  val _alpakkaFtp =
    ("com.lightbend.akka" %% "akka-stream-alpakka-ftp" % versionAlpakka).excludeAll(ExclusionRule("com.typesafe.akka"))

  val _alpakkaUnixDomainSocket =
    ("com.lightbend.akka" %% "akka-stream-alpakka-unix-domain-socket" % versionAlpakka)
      .excludeAll(ExclusionRule("com.typesafe.akka"))

  val _alpakkaMongodb = Seq(
    ("com.lightbend.akka" %% "akka-stream-alpakka-mongodb" % versionAlpakka)
      .excludeAll(ExclusionRule("com.typesafe.akka")),
    "org.mongodb.scala" %% "mongo-scala-bson" % "2.6.0")

  val _alpakkaCassandra =
    ("com.lightbend.akka" %% "akka-stream-alpakka-cassandra" % versionAlpakka).excludeAll(
      ExclusionRule("com.typesafe.akka"),
      ExclusionRule("com.datastax.cassandra"),
      ExclusionRule("io.netty"),
      ExclusionRule("com.google.guava"))

  val _alpakkaElasticsearch =
    ("com.lightbend.akka" %% "akka-stream-alpakka-elasticsearch" % versionAlpakka)
      .excludeAll(ExclusionRule("com.typesafe.akka"))

  val _alpakkaHbase =
    ("com.lightbend.akka" %% "akka-stream-alpakka-hbase" % versionAlpakka)
      .excludeAll(ExclusionRule("com.typesafe.akka"))

  val _alpakksHdfs =
    ("com.lightbend.akka" %% "akka-stream-alpakka-hdfs" % versionAlpakka).excludeAll(ExclusionRule("com.typesafe.akka"))

  val _alpakkaText =
    ("com.lightbend.akka" %% "akka-stream-alpakka-text" % versionAlpakka).excludeAll(ExclusionRule("com.typesafe.akka"))

  val _alpakkas = Seq(
    _alpakkaText,
    _alpakkaSimpleCodecs,
    _alpakkaXml,
    _alpakkaCsv,
    _alpakkaJsonStreaming,
    _alpakkaFile,
    _alpakkaFtp,
    _alpakkaUnixDomainSocket)

  val _alpakkaNoSQLs = Seq(
    _alpakkaMongodb,
    _alpakkaCassandra,
    //                           _alpakkaHbase,
    //                           _alpakksHdfs,
    _alpakkaElasticsearch)

  val _akkaStreamKafkas = Seq(
    ("com.typesafe.akka" %% "akka-stream-kafka" % versionAkkaStreamKafka)
      .exclude("com.typesafe.akka", "akka-slf4j")
      .cross(CrossVersion.binary))

  val _chillAkka = ("com.twitter" %% "chill-akka" % "0.9.3")
    .exclude("com.typesafe", "config")
    .exclude("org.scala-lang", "scala-library")
  val _neotypes = "com.dimafeng" %% "neotypes" % "0.5.0"
  val _config   = "com.typesafe" % "config" % "1.3.3"
  val _hanlp    = "com.hankcs" % "hanlp" % "portable-1.7.1"
  val _guice    = "com.google.inject" % "guice" % "4.2.2"

  val _circes =
    Seq("io.circe" %% "circe-core", "io.circe" %% "circe-generic", "io.circe" %% "circe-parser").map(_ % versionCirce)

  val _jacksons = Seq(
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8"          % versionJackson,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"        % versionJackson,
    "com.fasterxml.jackson.module"   % "jackson-module-parameter-names" % versionJackson,
    "com.fasterxml.jackson.module"   %% "jackson-module-scala"          % versionJackson)
  val _aspectjweaver = "org.aspectj" % "aspectjweaver" % "1.9.2"
  val _sigarLoader   = "io.kamon"    % "sigar-loader"  % "1.6.6" //-rev002"

  val _kamonAkka = ("io.kamon" %% "kamon-akka-2.5" % versionKamon)
    .excludeAll("com.typesafe.akka")
    .cross(CrossVersion.binary)
    .exclude("org.scala-lang", "scala-library")

  val _kamonAkkaHttp = ("io.kamon" %% "kamon-akka-http-2.5" % "1.1.1")
    .exclude("io.kamon", "kamon-akka-2.5")
    .cross(CrossVersion.binary)
    .exclude("com.typesafe.akka", "akka-http")
    .cross(CrossVersion.binary)
    .exclude("com.typesafe.akka", "akka-stream")
    .cross(CrossVersion.binary)
    .exclude("com.typesafe.akka", "config")
    .cross(CrossVersion.binary)
    .exclude("org.scala-lang", "scala-library")

  // need aspectjweaver
  val _kamonAkkaRemote = ("io.kamon" %% "kamon-akka-remote-2.5" % "1.1.0")
    .exclude("io.kamon", "kamon-akka-2.5")
    .cross(CrossVersion.binary)
    .excludeAll(ExclusionRule("com.typesafe.akka"))
    .cross(CrossVersion.binary)
    .exclude("org.scala-lang", "scala-library")

  val _kamonSystemMetrics = ("io.kamon" %% "kamon-system-metrics" % "1.0.0")
    .exclude("io.kamon", "kamon-core")
    .cross(CrossVersion.binary)
    .exclude("org.scala-lang", "scala-library")
  val _kamonPrometheus = "io.kamon" %% "kamon-prometheus" % "1.1.1"
  val _kamonZipkin     = "io.kamon" %% "kamon-zipkin" % "1.0.0"
  val _kamonLogback    = "io.kamon" %% "kamon-logback" % "1.0.4"
  val _kamons          = Seq(_kamonAkka, _kamonAkkaRemote, _kamonAkkaHttp, _kamonZipkin, _kamonPrometheus, _kamonSystemMetrics)

  val _scopt     = "com.github.scopt" %% "scopt"     % "3.7.1"
  val _osLib     = "com.lihaoyi"      %% "os-lib"    % "0.2.9"
  val _shapeless = "com.chuusai"      %% "shapeless" % "2.3.3"
  val _jwt       = "com.pauldijou"    %% "jwt-core"  % "2.1.0"
  val _requests  = "com.lihaoyi"      %% "requests"  % "0.1.8"

  val _slicks =
    Seq("com.typesafe.slick" %% "slick" % versionSlick, "com.typesafe.slick" %% "slick-testkit" % versionSlick % Test)

  val _doobies =
    Seq("org.tpolecat" %% "doobie-core" % versionDoobie, "org.tpolecat" %% "doobie-postgres" % versionDoobie)

  val _pois = Seq("org.apache.poi" % "poi-scratchpad" % versionPoi, "org.apache.poi" % "poi-ooxml" % versionPoi)

  val _logs =
    Seq("com.typesafe.scala-logging" %% "scala-logging" % "3.9.0", "ch.qos.logback" % "logback-classic" % "1.2.3")

  val _h2                = "com.h2database"          % "h2"                   % "1.4.197"
  val _bcprovJdk15on     = "org.bouncycastle"        % "bcprov-jdk15on"       % "1.61"
  val _quartz            = "org.quartz-scheduler"    % "quartz"               % versionQuartz
  val _mybatis           = "org.mybatis"             % "mybatis"              % "3.5.0"
  val _postgresql        = "org.postgresql"          % "postgresql"           % "42.2.5"
  val _mysql             = "mysql"                   % "mysql-connector-java" % "5.1.47"
  val _mssql             = "com.microsoft.sqlserver" % "mssql-jdbc"           % "6.4.0.jre8"
  val _hikariCP          = "com.zaxxer"              % "HikariCP"             % "3.3.1"
  val _protobuf          = "com.google.protobuf"     % "protobuf-java"        % "3.6.1"
  val _swaggerAnnotation = "io.swagger.core.v3"      % "swagger-annotations"  % "2.0.6"
  val _commonsVfs        = "org.apache.commons"      % "commons-vfs2"         % "2.2"
  val _jsch              = "com.jcraft"              % "jsch"                 % "0.1.55"
  val _nacosClient       = "com.alibaba.nacos"       % "nacos-client"         % "1.0.0"
  val _jakartaMail       = "com.sun.mail"            % "jakarta.mail"         % "1.6.3"
}
