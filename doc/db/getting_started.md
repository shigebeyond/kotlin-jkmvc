# Manipulate database

## 1 Add dependency
1. gradle
```
compile "net.jkcode.jkmvc:jkmvc-orm:1.9.0"
```

2. maven
```
<dependency>
    <groupId>net.jkcode.jkmvc</groupId>
    <artifactId>jkmvc-orm</artifactId>
    <version>1.9.0</version>
</dependency>
```

## 2 Configure dataSource

vim src/main/resources/dataSources.yaml

```
#  database name
default:
  # master database
  master:
    driverClassName: com.mysql.jdbc.Driver
    url: jdbc:mysql://127.0.0.1/test?useUnicode=true&characterEncoding=utf-8
    username: root
    password: root
  # multiple slave databases
  slaves:
    -
      driverClassName: com.mysql.jdbc.Driver
      url: jdbc:mysql://127.0.0.1/test?useUnicode=true&characterEncoding=utf-8
      username: root
      password: root
```

You can configure multiple database, as long as you use a different database name.

## 3 Get `Db` object

Database manipulation class is `net.jkcode.jkmvc.db.Db`, there are 2 usage
1. Manage database connections
2. Execute sql

Use `Db::instance(name:String = "default"):Db` to get `Db` object, it will get the configuration item corresponding to the `name` in file `dataSources.yaml`, and establish a database connection.

Just like:

```
// get `Db` object
val db = Db.instance();
```

## 4 Use the `Db` object to execute sql

Let's take a look at `Db` class's properties and methods

### 4.1 Metadata related properties and methods

Property / Method | Function
--- --- --- ---
dbType: DbType | Get the database type by driverClass
listColumns (table: String): List<String> | Get all the columns of the table
close(): Unit | Close database connection

### 4.2 Transaction-related methods

Method | Function
--- --- --- ---
begin(): Unit | Begin a transaction
commit(): Boolean | Commit the transaction
rollback(): Boolean | Rollbak the transaction
transaction (statement: Db.() -> T): T | Executes the transaction, encapsulating the transaction code with `begin()` / `commit()` / `rollback()`
isInTransaction(): Boolean | Check whether in a transaction

### 4.3 Update-sql executing method

Method | Function
--- --- --- ---
execute(sql: String, params: List<*> = emptyList<Any>(), generatedColumn: String? = null): Long | Execute a update-sql
batchExecute(sql: String, paramses: List<Any?>): IntArray | Batch update

### 4.4 Query-sql executing method

1. Low level method, which needs `transform`

Method | Function
--- --- --- ---
queryResult(sql: String, params: List<*> = emptyList<Any>(), transform: (DbResultSet) -> T): T | Query and get result with lambda
queryRows(sql: String, params: List<*> = emptyList<Any>(), transform: (DbResultRow) -> T): List<T> | Query multiple rows
queryRow(sql: String, params: List<*> = emptyList<Any>(), transform: (DbResultRow) -> T): T? | Query one row
queryColumn(sql: String, params: List<*> = emptyList<Any>(), clazz: KClass<T>? = null): List<T> | Query a column in multiple rows
inline queryColumn(sql: String, params: List<*> = emptyList<Any>()): List<T> | Query a column in multiple rows, `inline` saves a parameter
queryValue(sql: String, params: List<*> = emptyList<Any>(), clazz: KClass<T>? = null): T? | Query a value in a row
inline queryValue(sql: String, params: List<*> = emptyList<Any>()): T? | Query a value in a row, `inline` saves a parameter

2. High level method, which auto transform to target class's object

Method | Function
--- --- --- ---
queryMaps(sql: String, params: List<*> = emptyList<Any>(), convertingColumn: Boolean): List<Map<String, Any?>> | Query multiple rows, transform each row into `Map`
queryMap(sql: String, params: List<*> = emptyList<Any>(), convertingColumn: Boolean): Map<String, Any?>? | Query one row, and transform it into `Map`

### 4.5 Quote / Preview sql methods

Property / Method | Function
--- --- --- ---
previewSql(sql: String, params: List<*> = emptyList<Any>()): String | preview sql
quote(value: Any?): String | quote value
quoteColumn(column: CharSequence): String | Quoted column name
quoteTable(table: CharSequence): String | Quoted table name

### 4.6 Database field and object property name-transforming methods, used in the model

Property / Method | Function
--- --- --- ---
column2Prop(column: String): String | Get the object property name according the db field name
prop2Column(prop: String): String | Get the db property name according the object property name

## 5 Example

```
// get `Db` object
val db: Db = Db.instance()

// begin a transaction
db.transaction {
    // create a table
    db.execute("""
        CREATE TABLE IF NOT EXISTS `user` (
            `id` int(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '用户编号',
            `name` varchar(50) NOT NULL DEFAULT '' COMMENT '用户名',
            `age` tinyint(4) unsigned NOT NULL DEFAULT '0' COMMENT '年龄',
            `avatar` varchar(250) DEFAULT NULL COMMENT '头像',
            PRIMARY KEY (`id`)
        )ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='用户';
        """);

    // insert
    // val id = db.execute("insert into user(name, age) values(?, ?)" /*sql*/, listOf("shi", 1)/*sql paramters*/, "id"/*auto-increment id, as the return value*/) // return the auto generated id
    println("insert a user：" + id)

    // select single row
    val row = db.queryMap("select * from user limit 1" /*sql*/, emptyList<Any>() /*sql parameters*/) // return a row as `Map` object
    println("select a user：" + row)

    // count
    val count = db.queryValue<Int>("select count(1) from user" /*sql*/).get()!!
    println("count users: " + count)

    // update
    var f = db.execute("update user set name = ?, age = ? where id =?" /*sql*/, listOf("shi", 1, id) /*sql parameters*/, true /* convert column name into property name, eg: to_uid => toUid */) // return the updated rows count
    println("update a user：" + f)

    // select multiple rows
    val rows = db.queryMaps("select * from user limit 10" /*sql*/, emptyList<Any>() /*sql parameters*/, true /* convert column name into property name, eg: to_uid => toUid */) // return multiple rows as `Map` objects
    println("select multiple users: " + rows)

    // delete
    f = db.execute("delete from user where id =?" /*sql*/, listOf(id) /*sql parameters*/) // return the deleted rows count
    println("delete a user：" + f)
}
```