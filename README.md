# Conta-Triangoli

## Introduzione
Lo scopo di questo progetto è di formulare un algoritmo che, dato un grafo, restituisce il numero q<sub>3</sub> di 3-cliques esistenti al suo interno. Facciamo riferimento all'articolo *"Clique Counting in MapReduce: Algorithms and Experiments"*, limitatamente al caso k=3 , e implementiamo secondo il paradigma Map-Reduce lo pseudocodice dell'algoritmo. Il codice è stato scritto con *Java* e *Spark* ed è stato utilizzato come strumento di supporto il database NoSQL *Neo4j*.


## Il dataset  
Dove trovarlo: https://snap.stanford.edu/data/loc-Gowalla.html  
Il grafo modella una struttura dati con relazioni indirette, e ha 196591 nodi, 950327 archi e 2273138 triangoli. Il sito rende disponibile il grafo diretto -quindi possiede 1900654 archi- e i nodi di ogni arco sono separati da uno spazio.



## Presentazione dei file
Nella cartella Github si trovano i seguenti file/classi:  

| File        | Descrizione           |
|:---------- |:------------- |
| `GowallaNodi.csv` | file contenente i nodi del grafo |
| `GowallaArchi.csv` | file contenente gli archi del grafo -indiretto- |
| `Gowalla.txt` | file contenente gli archi del grafo diretto  |
| `ArcoGradiGowalla_pt1.txt` | prima suddivisione del file contenente gli archi e i gradi dei rispettivi nodi  |
| `ArcoGradiGowalla_pt2.txt` | seconda suddivisione del file contenente gli archi e i gradi dei rispettivi nodi |

| Classe        | Descrizione           |
|:---------- |:------------- |
| `Arco.java` | classe *wrapper* utilizzata per la preparazione dell'input per l'applicazione |
| `ContaTriangoli.java` | classe main dell'applicazione che lavora esclusivamente con *Spark* |
| `ContaTriangoli_NeoSpark.java` | classe main dell'applicazione che lavora congiuntamente con *Spark* e *Neo4j* |
| `Map2.java` | interfaccia che implementa Map2 |
| `Map3.java` | interfaccia che implementa Map3 |
| `Card.java` | interfaccia che conta coppie di valori |



## Indicazioni per l'uso
Per una maggiore leggibilità del grafo, con il seguente codice abbiamo posto come elemento separatore dei due nodi la virgola:

```
JavaRDD<String> gowalla = jsc.textFile("data/Gowalla_edges.txt");
gowalla = gowalla.map(x->new String(x.split("	")[0]+ "," + x.split("	")[1]));
gowalla.saveAsTextFile("Gowalla");
```
Per la creazione del grafo su *Neo4j* si richiede un file contenente la lista dei nodi del grafo. Per ottenerla, abbiamo utilizzato il seguente codice:

```
JavaRDD<String> dGrafo = jsc.textFile("data/Gowalla.txt");	
JavaRDD<String> dNodi = dGrafo.map(x -> new String(x.split(",")[0],1)).reduceByKey((x,y)->x+y).map(x -> new String(x._1 + "," + x._1));
dNodi.saveAsTextFile("GowallaNodi");
```

E' richiesto inoltre un file contenente la lista degli archi del grafo indiretto. Con il seguente codice, facendo uso della classe *wrapper* `Arco.java`, abbiamo dunque reso il grafo da diretto a indiretto:
 
```
JavaRDD<String> dGrafo = jsc.textFile("data/Gowalla.txt");	
JavaRDD<Arco> dArco = dGrafo.map(x -> new Arco(x.split(",")[0],x.split(",")[1]));
List<Arco> A = dArco.collect();
List<String> Grafo = new ArrayList<String>();
 for (Arco a : A) {
  if (Integer.parseInt(a.getIdEntrata()) < Integer.parseInt(a.getIdUscita())) {
  Grafo.add(a.getIdEntrata() + "," + a.getIdUscita());
 }
JavaRDD<String> dGrafo1 = jsc.parallelize(Grafo);
dGrafo1.saveAsTextFile("GowallaArchi");
```

Una volta ottenuti i file `GowallaNodi.txt` e `GowallaArchi.txt`, li abbiamo trasformati in file `.csv` e utilizzando la libreria `apoc` di *Neo4j* li abbiamo caricati sul software.

## Due strade alternative 

Abbiamo scelto di seguire due strategie che forniscono in maniera diversa l'input per l'algoritmo *Clique Counting*. La prima genera l'input direttamente con *Spark*, mentre nella seconda è *Neo4j* che svolge la parte iniziale di preparazione per l'input dell'applicazione. Successivamente, una volta terminato l'algoritmo, *Neo4j* è stato usato nuovamente come strumento per validare i risultati ottenuti.

1. *Preparazione dell'input con *Spark**:  
Dopo aver caricato il file `Gowalla.txt` sull'applicazione `ContaTriangoli.java`, inizia la parte di codice che ha lo scopo di produrre in output una lista in cui in ogni riga si trova il generico elemento {u, v, d(u), d(v)}. 
Per fare ciò, abbiamo eseguito i seguenti passaggi:

| Passaggio        | Descrizione           |
|:---------- |:------------- |
| `Calcolo di:(NODO,GRADO)` | Al grafo diretto abbiamo applicato una funzione lambda che restituisce un oggetto in cui in chiave si trova il nodo in entrata, e in valore l'intero 1; successivamente, mediante una `reduceByKey`, abbiamo ottenuto una lista di tuple aventi in chiave un nodo, e in valore il suo grado; per semplicità, abbiamo poi convertito quest'ultima in una lista di stringhe. |
| `Calcolo di:(ARCO,GRADI)` | Abbiamo poi creato due liste di tuple differenti aventi entrambe come chiave l'arco, e come valore rispettivamente il grado del nodo in entrata e il grado del nodo in uscita. Con un `join`, intersecando per chiave le due liste, abbiamo ottenuto una lista di tuple aventi come chiave l'arco, e come valore i gradi dei relativi nodi. Sempre per comodità, abbiamo poi convertito questo oggetto in una lista di stringhe. |


2. *Preparazione dell'input con *Neo4j**:
Dopo aver creato il grafo su Neo4j, abbiamo eseguito i seguenti passaggi: 

| Passaggio        | Descrizione           |
|:---------- |:------------- |
| `Calcolo di:(NODO,GRADO)` | Abbiamo eseguito una query che assegna come attributo ad ogni nodo del grafo il relativo grado. Questa operazione è stata ottimizzata utilizzando il comando `node.degree()` della libreria `apoc`. | 
| `Calcolo di:(ARCO,GRADI)` | Per esportare la lista in cui il generico elemento è del tipo {u, v, d(u), d(v)}, abbiamo utilizzato il comando `export.csv.query()` della libreria `apoc` in cui come argomento è richiesta la query che permette di ottenere questo oggetto. Il file risultante è del tipo `.csv`, ma per fornirlo in input all'applicazione `ContaTriangoli_NeoSpark.java`, l'abbiamo convertito nel file `ArcoGradi.txt`, che nella cartella Github è presente suddiviso in due file. 

## Implementazione dell'algoritmo con Java e Spark
L'algoritmo è diviso in tre round MapReduce:

**ROUND 1**

**Map 1**: Data in input la lista di stringhe contenente ogni arco con accanto i gradi dei relativi nodi, con un'operazione di `filter` abbiamo selezionato gli archi aventi grado del nodo in entrata strettamente minore del grado del nodo in uscita e abbiamo salvato questo oggetto nella `JavaRDD` di stringhe `dMap1_0`.
Successivamente, con una seconda operazione di `filter`, abbiamo selezionato gli archi aventi grado del nodo in entrata uguale al grado del nodo in uscita e abbiamo eseguito un ulteriore `filter` che ha selezionato, di questi, solamente quelli che possedevano etichetta numerica del nodo in entrata inferiore a quella del nodo in uscita; abbiamo poi salvato questo oggetto nella `JavaRDD` di stringhe `dMap1_1`. Abbiamo poi unito i due oggetti per ottenere tutti gli archi (u,v) tali che u &pr; v. Con lo scopo di ottenere &Gamma;<sup>+</sup>(u), abbiamo infine eseguito una `reduceByKey` sull'output precedente: questo ci ha restituito la `JavaPairRDD`  `dGammaPiu`, in cui la generica coppia chiave-valore è del tipo (u; v<sub>1</sub>, d(v<sub>1</sub>), v<sub>2</sub>,d(v<sub>2</sub>),...), ovvero l'oggetto avente come chiave il nodo, e come valore l'insieme &Gamma;<sup>+</sup>(u), con in aggiunta l'informazione circa il grado dei nodi in esso contenuti; questa scelta è stata fatta per permettere l'implementazione dei passi successivi.


**Reduce 1**: Abbiamo utilizzato l'interfaccia `Card.java` sull'output del passo precedente: questa conta il numero di termini separati da virgole all'interno del valore di ogni chiave e successivamente divide questo numero per 2. In questo modo siamo riusciti ad ottenere la coppia `JavaPairRDD<String, Integer>` avente come chiave il nodo e come valore la cardinalità del relativo insieme &Gamma;<sup>+</sup>(u). Poi, con un'operazione di `filter`, abbiamo selezionato solamente le coppie che avevano cardinalità maggiore o uguale a 2, e abbiamo salvato questo oggetto nella variabile `dReduce1_0`. Per ottenere l'output del *Reduce 1*, che abbiamo salvato nell'oggetto `dReduce1_1`,  abbiamo infine eseguito un `join` tra l'oggetto appena creato e `dGammaPiu`; il risultato è stato poi convertito in una `JavaRDD` di stringhe e privato dell'informazione circa la cardinalità di &Gamma;<sup>+</sup>(u). Abbiamo in questo modo ottenuto un oggetto avente come generico elemento (u, v<sub>1</sub>, d(v<sub>1</sub>), v<sub>2</sub>,d(v<sub>2</sub>),...).

**ROUND 2**

**Map 2**: Per il primo input, abbiamo utilizzato l'output del *Map 1* e abbiamo creato la `JavaPairRDD` `dMap2_0` avente in chiave l'arco e in valore il simbolo "$".
Per il secondo input, abbiamo utilizzato l'interfaccia `Map2.java`.  Avendo mantenuto l'informazione riguardo i gradi dei nodi appartenenti a &Gamma;<sup>+</sup>(u), siamo riusciti a confrontarli. 
Per fare ciò, abbiamo implementato due cicli `for`:
- il primo parte da i=2 e viene incrementato a ogni iterazione in modo da scorrere lungo i numeri del valore della tupla posti in posizione pari. In questo modo abbiamo selezionato i gradi di ogni nodo in quanto situati alla destra dell'etichetta di ognuno di essi. 
- il secondo parte da j=i+2 e procede nello stesso modo. 

Abbiamo poi salvato nell'oggetto `dMap2_1` tutte le coppie di nodi di &Gamma;<sup>+</sup>(u) - ottenuti scalando le posizioni correnti rispetto ai cicli `for` di un'unità- che soddisfavano la condizione x<sub>i</sub> &pr; x<sub>j</sub>. Abbiamo dunque ottenuto l'output richiesto dal Map 2, ovvero (x<sub>i</sub>,x<sub>j</sub>);u).

  
**Reduce 2**: Abbiamo creato l'oggetto `dReduce2_0` utilizzando una `reduceByKey` grazie alla quale abbiamo selezionato tutte le coppie del passo precedente che avevano la stessa chiave, aggregandone i valori. Successivamente, abbiamo eseguito un `join` tra l'oggetto appena creato e il primo output di *Map 2* contenuto nell'oggetto `dMap2_0`, creando la `JavaPairRDD` `dReduce2_1`. In questo modo, abbiamo selezionato gli elementi di &Gamma;<sup>+</sup>(u) che erano collegati da un arco.

**ROUND 3** 

**Map 3**: Abbiamo utilizzato l'interfaccia `Map3.java` su `dReduce2_1`: per ogni nodo presente nel valore della tupla, abbiamo generato una nuova coppia avente come chiave il nodo, e come valore la chiave della tupla precedente.

**Reduce 3**: Eseguendo una `reduceByKey` sull'output appena ottenuto, abbiamo costruito, per ogni chiave data in input, l'insieme contenente gli archi di G<sup>+</sup>(u). Poi, utilizzando nuovamente l'interfaccia `Card.java`, abbiamo contato il numero di archi in esso contenuti. In conclusione, mediante un'ulteriore `reduceByKey` che ha sommato i valori delle tuple aggregate per chiave, abbiamo ottenuto il numero di triangoli presenti nel grafo.



## Interrogazione del grafo su *Neo4j*

Facendo riferimento all'applicazione `ContaTriangoli.java`, abbiamo creato il grafo su *Neo4j* nello stesso modo in cui è stato fatto inizialmente per l'applicazione `ContaTriangoli_NeoSpark.java`. 
Collegandoci al software *Neo4j*, e focalizzandoci su un particolare nodo di prova, abbiamo eseguito delle query che mostrassero a schermo i seguenti oggetti richiamati dall'algoritmo:
* grado del nodo e insieme dei nodi ad esso collegati 
* insieme &Gamma;<sup>+</sup>(u) per il nodo di riferimento
* tutti i nodi che formano dei triangoli con il nodo di riferimento
* triangoli contati dal nodo di riferimento
	 
Simultaneamente abbiamo verificato gli output delle query di *Neo4j* attraverso operazioni di `filter` con *Spark*.
		
