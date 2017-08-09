import org.apache.spark.sql.DataFrame

import org.apache.log4j.{Level, Logger}

/**
  * Created by mmami on 26.01.17.
  */
object Main extends App {

    Logger.getLogger("ac.biu.nlp.nlp.engineml").setLevel(Level.OFF);
    Logger.getLogger("org.BIU.utils.logging.ExperimentLogger").setLevel(Level.OFF);
    Logger.getRootLogger().setLevel(Level.OFF);

    if (args.length == 1)
        println(s"Hello, ${args(0)}")
    else
        println("I didn't get your name.")

    // 1. Read SPARQL query
    println("\n/*******************************************************************/")
    println("/*                         QUERY ANALYSIS                          */")
    println("/*******************************************************************/")
    var queryFile = Config.get("query")

    val queryString = scala.io.Source.fromFile(queryFile)
    val query = try queryString.mkString finally queryString.close()

    // 2. Extract star-shaped BGPs
    var qa = new QueryAnalyser(query)
    var stars = qa.getStars()
    val prefixes = qa.getProlog()

    println("\n- Predicates per star:")
    for(v <- stars) {
        println("* " + v._1 + ") " + v._2)
    }

    // 3. Generate plan of joins
    println("\n/*******************************************************************/")
    println("/*                         PLAN GENERATION                         */")
    println("/*******************************************************************/")
    var pl = new Planner(stars)
    var pln = pl.generateJoinPlan()
    var srcs = pln._1
    var joinFlags = pln._2

    srcs(0)

    // 4. Check mapping file
    println("---> MAPPING CONSULTATION")
    var mappingsFile = Config.get("mappings.file")
    var mappers = new Mapper(mappingsFile)
    var results = mappers.findDataSources(stars)
    var star_df : Map[String, DataFrame] = Map()

    println("\n- The following are the join variables: " + joinFlags)

    println("\n---> GOING TO SPARK NOW")
    for(s <- results) {
        val star = s._1
        val datasources = s._2
        val options = s._3

        //println("Start: " + star)

        var spark = new Sparking(Config.get("spark.url"))

        var ds : DataFrame = null
        if(joinFlags.contains(star)) {
            //println("TRUE: " + star)
            //println("->datasources: " + datasources)
            ds = spark.query(datasources, options, true)
            println("...with DataFrame schema: ")
            ds.printSchema()
        } else {
            //println("FALSE: " + star)
            //println("->datasources: " + datasources)
            ds = spark.query(datasources, options, false)
            println("...with DataFrame schema: ")
            ds.printSchema()
        }

        //ds.collect().foreach(s => println(s))

        star_df += (star -> ds) // DataFrame representing a star
    }

    println("\n/*******************************************************************/")
    println("/*                         QUERY EXECUTION                         */")
    println("/*******************************************************************/")
    println("- Here are the (Star, DataFrame) pairs: " + star_df)
    var df_join : DataFrame = null

    println("- Here are join pairs: " + srcs + "\n")
    for(v <- srcs) {
        println("- DF1 of (" + v._1 + ") joins DF2 of (" + v._2 + ") using [" + Helpers.omitNamespace(v._3) + " (from " + v._3 + ") = ID]")
        val df1 = star_df(v._1)
        val df2 = star_df(v._2)

        println("DF1: ")
        //df1.collect().foreach(t => println(t.getAs("author")))
        //df1.printSchema()
        df1.show()

        println("DF2: ")
        //df2.collect().foreach(z => println(z.getAs("ID")))
        //df2.printSchema()
        df2.show()
        df_join = df1.join(df2, df1.col(Helpers.omitNamespace(v._3)).equalTo(df2("ID"))) // people.col("deptId").equalTo(department("id"))

    }

    println("- Final results DF schema: ")
    df_join.printSchema()

    println("results: ")
    df_join.show()
    //df_join.collect().foreach(t => println(t))

}