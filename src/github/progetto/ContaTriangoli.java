package github.progetto;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.AnalysisException;
import scala.Tuple2;

public class ContaTriangoli {
	public static void main(String[] args) throws AnalysisException {

		Logger.getLogger("org").setLevel(Level.ERROR);
		Logger.getLogger("akka").setLevel(Level.ERROR);

		Logger.getLogger("org").setLevel(Level.ERROR);
		Logger.getLogger("akka").setLevel(Level.ERROR);
		SparkConf sc = new SparkConf();
		sc.setAppName("Conta Triangoli");
		sc.setMaster("local[*]");
		JavaSparkContext jsc = new JavaSparkContext(sc);
				
		//// ****INIZIO****

		JavaRDD<String> dGrafo = jsc.textFile("data/Gowalla.txt");
		
		// **PREPARAZIONE DEL GRAFO**
		
		JavaPairRDD<String, String> dGrado = dGrafo.mapToPair(x -> new Tuple2<String, Integer>(x.split(",")[0], 1)).reduceByKey((x, y) -> x + y).mapToPair(z -> new Tuple2<String, String>(z._1.split(",")[0], Integer.toString(z._2)));

		JavaPairRDD<String, String> dChiaveArco0 = dGrafo.mapToPair(x -> new Tuple2<String, String>(x.split(",")[0], x));

		JavaPairRDD<String, String> dChiaveArco1 = dGrafo.mapToPair(x -> new Tuple2<String, String>(x.split(",")[1], x));

		JavaPairRDD<String, String> dArcoG0 = dChiaveArco0.join(dGrado).mapToPair(x -> new Tuple2<String, String>(x._2._1, x._2._2));

		JavaPairRDD<String, String> dArcoG1 = dChiaveArco1.join(dGrado).mapToPair(w -> new Tuple2<String, String>(w._2._1, w._2._2));

		JavaRDD<String> dArcoGradi = dArcoG0.join(dArcoG1).map(x -> new String(x._1 + "," + x._2._1 + "," + x._2._2));

		// **MAP1**
		
		JavaRDD<String> dMap1_0 = dArcoGradi.filter(x -> Integer.parseInt(x.split(",")[2]) < Integer.parseInt(x.split(",")[3]));
		
		JavaRDD<String> dMap1_1 = dArcoGradi.filter(x -> Integer.parseInt(x.split(",")[2]) == Integer.parseInt(x.split(",")[3])).filter(x -> Integer.parseInt(x.split(",")[0]) < Integer.parseInt(x.split(",")[1]));
		
		JavaRDD<String> dMap1_2 = dMap1_0.union(dMap1_1);

		JavaPairRDD<String, String> dGammaPiu = dMap1_2.mapToPair(x -> new Tuple2<String, String>(x.split(",")[0], x.split(",")[1] + "," + x.split(",")[3])).reduceByKey((x, y) -> x + "," + y);

		// **REDUCE1**
		
		JavaPairRDD<String, Integer> dReduce1_0 = dGammaPiu.mapToPair(new Card()).filter(x -> x._2 >= 2);
		
		JavaRDD<String> dReduce1_1 = dGammaPiu.join(dReduce1_0).map(x -> new String(x._1 + "," + x._2._1));

		// **MAP2**
		
		JavaPairRDD<String, String> dMap2_0 = dMap1_2.mapToPair(x -> new Tuple2<String, String>(x.split(",")[0] + "," + x.split(",")[1], "$"));

		JavaPairRDD<String, String> dMap2_1 = dReduce1_1.flatMapToPair(new Map2());

		// **REDUCE2**
		
		JavaPairRDD<String, String> dReduce2_0 = dMap2_1.reduceByKey((x, y) -> x + "," + y);

		JavaPairRDD<String, String> dReduce2_1 = dReduce2_0.join(dMap2_0).mapToPair(x -> new Tuple2<String, String>(x._1, x._2._1));

		// **MAP3**
		
		JavaPairRDD<String, String> dMap3_0 = dReduce2_1.flatMapToPair(new Map3());

		// **REDUCE3**
		
		JavaPairRDD<String, String> dReduce3_0 = dMap3_0.reduceByKey((x, y) -> x + "," + y);

		JavaPairRDD<String, Integer> dReduce3_1 = dReduce3_0.mapToPair(new Card());

		Integer numeroTriangoli = dReduce3_1.values().reduce((x, y) -> x + y);

		System.out.println(numeroTriangoli);
	    
		//// ******FINE******

		//// ****QUERY****
		
		//call apoc.import.csv( [{fileName: 'file:/GowallaNodi.csv', labels: ['nodo']}],[{fileName: 'file:/GowallaArchi.csv', type: 'vicino'}],{delimiter: ',', arrayDelimiter: ' ', stringIds: false})
		
		//match(u) set u.grado = apoc.node.degree(u)
	    		
		//int nodoProva = 147;

		//JavaPairRDD<String, String> dVerifica0 = dGrafo.mapToPair(x -> new Tuple2<String, String>(x.split(",")[0], x.split(",")[1])).reduceByKey((x, y) -> x + "," + y).filter(x -> Integer.parseInt(x._1) == nodoProva);
		
		//JavaPairRDD<String, String> dVerifica1 = dGrado.filter(x -> Integer.parseInt(x._1) == nodoProva);
		
		//System.out.println(dVerifica0.collect());
		
		//System.out.println(dVerifica1.collect());
		
		//match (u:nodo) where u.id = 147 return u.id,u.grado
				
		//match (u:nodo)-[:vicino]-(v:nodo) where u.id = 147 return u,v

		//JavaPairRDD<String, String> dVerifica2 = dMap1_2.mapToPair(x -> new Tuple2<String, String>(x.split(",")[0], x.split(",")[1])).reduceByKey((x, y) -> x + "," + y).filter(x -> Integer.parseInt(x._1) == nodoProva);
		
		//System.out.println(dVerifica2.collect());
		
		//match (u:nodo)-[:vicino]-(v:nodo) where u.id = 147 match (u:nodo)-[:vicino]-(v:nodo) where u.grado < v.grado or (u.grado = v.grado and u.id < v.id) return u,v

		//JavaPairRDD<String, String> dVerifica3 = dReduce3_0.filter(x -> Integer.parseInt(x._1) == nodoProva);
		
		//System.out.println(dVerifica3.collect());
		
		//match (v:nodo)-[:vicino]-(u:nodo)-[:vicino]-(w:nodo) where u.id = 147 match (v:nodo)-[:vicino]-(w:nodo) return u,v,w

		//match (v:nodo)-[:vicino]-(u:nodo)-[:vicino]-(w:nodo) where u.id = 147 match (w:nodo)-[:vicino]-(v:nodo) where (u.grado < v.grado or (u.grado = v.grado and u.id < v.id)) and ( u.grado < w.grado or (u.grado = w.grado and u.id < w.id)) return u,v,w
		
	}

}
