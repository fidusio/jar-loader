## jar-loader
A java jar loader that expand a fat java jar file containing jars and classes then execute a main-class with its parameters

## Usage
Type: <strong>java -jar jar-loader.jar</strong>
<ul>
<li><strong>java -jar jar-loader.jar [-f] [-jar] &lt;app-fat.jar&gt; [main-class] [parameters...]</strong>
<li><strong>[-f]</strong>: if specified expand the jar files inside app-fat.jar in a temp dir of the file system if omitted will use jimfs(in memory file system).
<li><strong>app-fat.jar</strong>: the required jar file that contain all the .class(es) and jars required by the app.
<li><strong>[-jar]</strong>: if the app-fat.jar has a main-class.
<li><strong>[main-class]</strong>: required if [-jar] was omitted.
<li><strong>[parameters]</strong>: if required by the main-class.
</ul>

Example: <strong>java -jar jar-loader.jar -jar xlogistx-fat-jar-with-dependencies.jar http-server-config.json</strong>

## The fat jar content

<pre>
app-fat.jar
├── META-INF/
│   └── MANIFEST.MF
├── com/
│   └── acme/
│       ├── Main.class
│       └── utils/
│           └── Helper.class
├── resources/
│   └── config.properties
└── lib/
    ├── zoxweb-core-2.3.8.jar
    ├── guava-32.1.2.jar
    ├── slf4j-api-2.0.13.jar
    ├── logback-classic-1.5.6.jar
    ├── logback-core-1.5.6.jar
    ├── jackson-core-2.17.2.jar
    ├── jackson-databind-2.17.2.jar
    ├── jackson-annotations-2.17.2.jar
    └── commons-lang3-3.14.0.jar
└── somewhere-else/
    ├── custom-1.jar
    └── lala-land.jar
</pre>

## How does it work
<ol>
<li>The jar-loader will open the fat jar search all the files that ends with .jar expand them into the file system
<li>Load all the jar(s) into the class loader
<li>Load all the class(es) into the class loader
<li>Locate the main-class
<li>Invoke main-class.main(parameters...) 
</ol>

## Where to get jar-loader.jar
You can download [jar-loader.jar](https://xlogistx.io/apps/jar-loader.jar)
<br />
<pre>
File info:
{
  "filename": "jar-loader.jar",
  "length": 3285121,
  "sha-256": "C2DBCA1EFF8C2FF6FFF9EFF685ECF20650B001290E8683E531A007D3F55801AA"
}
</pre>