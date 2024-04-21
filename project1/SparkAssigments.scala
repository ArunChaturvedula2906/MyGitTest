package org.assigment.com

import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions.{col, explode}
import org.apache.spark.sql.functions._

import java.util.Properties

object SparkAssigments extends  App {
  Logger.getLogger("org").setLevel(Level.WARN)
  val spark = SparkSession.builder()
    .appName("Assigments")
    .master("local[*]")
    .getOrCreate()

  /*
  Word count using Data Frame and load into CSV file
   */

  val wordCountDf = spark.read
    .textFile("C:\\Users\\obyon\\OneDrive\\Documents\\Arun\\ProjectPractice\\Spark\\Input\\*")
  val filterWordCount = wordCountDf.select(explode(split(lower(col("value")), "\\s+")).alias("word")).groupBy("word").count()
    .withColumnRenamed("count","Wordcountvalue")
  val filterWordCount1 = filterWordCount.orderBy(desc("Wordcountvalue"))
  filterWordCount1.repartition(1)
    .write.csv("C:\\Users\\obyon\\OneDrive\\Documents\\Arun\\ProjectPractice\\Spark\\Output\\Wordcount1.csv")
  filterWordCount1.coalesce(1)
   .write.csv("C:\\Users\\obyon\\OneDrive\\Documents\\Arun\\ProjectPractice\\Spark\\Output\\Wordcount.csv")




  /*
  Task2  COnnect with Database
   */
  ///JDBC Url with Username and password .
  val url = "jdbc:mysql://localhost:3306/challenge1"
  val username ="root"
  val password = "Sql@1234"
  // JDBC Connection properties
  val connectionProperties = new Properties()
  connectionProperties.put("user",username)
  connectionProperties.put("password",password)

  //Read data from DB Level
  val memberTable = "members"
  val menuTable  = "menu"
  val salesTable = "sales"

  val memberTableDF = spark.read.jdbc(url,memberTable,connectionProperties)
  val menuTableDF =  spark.read.jdbc(url,menuTable,connectionProperties)
  val saleTableDF = spark.read.jdbc(url,salesTable,connectionProperties)
  //memberTableDF.show()
  //menuTableDF.show()
  //saleTableDF.show()

  /*
  What is the total amount each customer spent at the restaurant?
   */
  val salesMembersDF = menuTableDF.alias("m").join(saleTableDF.alias("s"),
    col("m.product_id")===col("s.product_id"),"inner")
   .groupBy("customer_id").sum("price")

  val percustomer = salesMembersDF.withColumnRenamed("price","total_price")
  salesMembersDF.show()

  /*
  Task 3 :
  How many days has each customer visited the restaurant?
   */
  salesMembersDF.groupBy("customer_id").agg(count("s.order_date").alias("visted_date_count")).show()

  /*
  Task 4
  What was the first item from the menu purchased by each customer?
   */

  val first_item = saleTableDF.groupBy("customer_id").agg(min("order_date").alias("first_order"))
  //first_item.show()
  val purchasedEachCustomer =
    saleTableDF.alias("s").join(menuTableDF.alias("m"),
    col("s.product_id")===col("m.product_id"),"inner")
    .join(first_item.alias("f"),
    col("s.customer_id")===col("f.customer_id"),"inner")
  purchasedEachCustomer.show()

  /*
  Task 5 :
  What is the most purchased item on the menu and how many times was it purchased by all customers?
   */
  saleTableDF.alias("s").join(menuTableDF.alias("m"),
    col("s.product_id")===col("m.product_id"),"inner").groupBy(col("s.product_id"))
    .agg(count(col("s.product_id")).alias("count_value")).orderBy(col("count_value").desc).show()


  /*
   Task 6 :Which item was the most popular for each customer?
   */
  val mostPopularDF = menuTableDF.alias("m").join(saleTableDF.alias("s"),
    col("m.product_id") === col("s.product_id"),"inner")
    .groupBy(col("s.customer_id"),col("m.product_name"))
    .agg(count(col("m.product_id")).alias("order_count"))
  val windowSpec1 = Window.partitionBy(col("customer_id")).orderBy(desc("customer_id"))
  val randDF = mostPopularDF.withColumn("rank_value",dense_rank().over(windowSpec1))
  randDF.select(col("customer_id"),col("product_name"),col("order_count"))
    .where(col("rank_value")===1).show()



  /*
  Task 7 :
  Which item was purchased first by the customer after they became a member?
*/
  val joinedAsMemberDF = memberTableDF.alias("m").join(saleTableDF.alias("s"),
    col("m.customer_id")===col("s.customer_id") && col("s.order_date") > col("join_date"),"inner")

  val windowSpec = Window.partitionBy(col("m.customer_id")).orderBy(col("s.order_date"))
  val row_numberDF = joinedAsMemberDF.withColumn("row_num_value",row_number().over(windowSpec))
  row_numberDF.alias("jm").join(menuTableDF.alias("m"),col("jm.product_id")===col("m.product_id")
  ,"inner")
  .select("jm.customer_id","product_name").where(col("row_num_value")===1).orderBy(col("customer_id"))
    .show()

  /*
  Task 8:
  Which item was purchased just before the customer became a member?


   */
  //I need to register all the source table
  saleTableDF.createOrReplaceTempView("sales")
  menuTableDF.createOrReplaceTempView("menu")
  memberTableDF.createOrReplaceTempView("members")


  spark.sql("WITH purchased_before_member AS " +
    "(SELECT members.customer_id, sales.product_id, " +
    "ROW_NUMBER() OVER(PARTITION BY members.customer_id ORDER BY sales.order_date DESC) AS rank_value " +
    "FROM members " +
    "JOIN sales ON members.customer_id = sales.customer_id AND sales.order_date < members.join_date " +
    ") " +
    "SELECT p_member.customer_id,menu.product_name FROM purchased_before_member AS p_member JOIN menu " +
    "ON p_member.product_id = menu.product_id " +
    "WHERE rank_value = 1 ORDER BY p_member.customer_id ASC").show()



  /*

  Task 9:What is the total items and amount spent for each member before they became a member?

   */

  spark.sql("SELECT sales.customer_id, COUNT(sales.product_id) AS total_items, " +
    "SUM(menu.price) AS total_sales FROM sales " +
    "JOIN members ON sales.customer_id = members.customer_id " +
    "AND sales.order_date < members.join_date JOIN menu ON sales.product_id = menu.product_id " +
    "GROUP BY sales.customer_id ORDER BY sales.customer_id ").show()



  /*
  Task 10:
  If each $1 spent equates to 10 points and sushi has a 2x points multiplier - how many points would each customer have?
   */
  spark.sql("WITH points_table AS " +
    "(SELECT menu.product_id, " +
    "CASE WHEN product_id = 1 THEN price * 20 ELSE price * 10 END AS points " +
    "FROM menu ) SELECT  sales.customer_id, SUM(points_table.points) AS total_points " +
    "FROM sales JOIN points_table ON sales.product_id = points_table.product_id " +
    "GROUP BY sales.customer_id ORDER BY sales.customer_id ").show()
  spark.stop()


}
