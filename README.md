# Triangle-Counting

## Introduzione
Lo scopo di questo progetto è di formulare un algoritmo che, dato un grafo restituisce il numero q<sub>3</sub> di 3-cliques esistenti al suo interno. Facciamo riferimento all'articolo "*Clique Counting in MapReduce: Algorithms and Experiments*", limitatamente al caso k=3 , e implementiamo secondo il paradigma *Map-Reduce* lo pseudocodice dell'algoritmo. Il codice è stato scritto con *Java* e *Spark* e viene utilizzato come strumento di supporto il database NoSQL *Neo4J*.

## Notazione utilizzata e teoria dei grafi 
Dato un grafo indiretto G=(V,E) dove V corrisponde all'insieme dei vertici ed E all'insieme archi, una k-clique è un sottoinsieme C di V di cardinalità k tale ogni coppia u,v appartenente a C è collegata da un arco. Più formalmente, una k-clique è un insieme di vertici tale che il sottografo indotto da questi è completo, dove un sottografo indotto è un sottografo G'=(V',E') di G con V' &sube; V, E' &sube; E tale che &forall; u &isin; V', v &isin; V' per cui vale che (u,v) &isin; E allora si ha che (u,v) &isin; E'. 
Si introduce una relazione di ordinamento tra i vertici del grafo definita come segue: u &pr; v &hArr; d(u) < d(v) oppure d(u) = d(v) e u < v, dove d(u) = |&Gamma;(u)|, dove &Gamma;(u) è l'insieme dei vertici collegati tramite un arco ad u. L'algoritmo utilizza questo ordinamento per determinare il nodo di riferimento per il conteggio di ciascuna clique.

## La strategia dell'algoritmo
Otteniamo &Gamma;<sup>+</sup>(u) &forall; u &isin; U, ovvero l'insieme di tutti i v &isin; &Gamma;(u) tali che: u &pr; v, tra questi si selezionano poi gli insiemi tali per cui:  
|&Gamma;<sup>+</sup>(u)| &ge; k-1. Ottenuto questo, attraverso passaggi logici, ricaviamo G<sup>&#43;</sup>(u), ovvero il sottografo indotto da &Gamma;<sup>&#43;</sup>(u), e poi contiamo le k-1 cliques in G<sup>+</sup>(u).

## Il dataset  
Dove trovarlo: https://snap.stanford.edu/data/loc-Gowalla.html  
Il grafo modella una struttura dati con relazioni indirette, e ha 196591 nodi, 950327 archi e 2273138 triangoli. Il sito però, rende disponibile il grafo diretto, quindi possiede 1900654 archi e i nodi di ogni arco sono separati da uno spazio. 

## Indicazioni per l'uso
Per una maggiore leggibilità del grafo, con il seguente codice abbiamo posto come elemento separatore dei due nodi la virgola.

```
JavaRDD<String> gowalla = jsc.textFile("data/Gowalla_edges.txt");
gowalla = gowalla.map(x->new String(x.split("	")[0]+ "," + x.split("	")[1]));
gowalla.saveAsTextFile("Gowalla");
```
Per la creazione del grafo su *Neo4J* si richiede un file contenente la lista dei nodi del grafo. Per ottenerla, utilizziamo il seguente codice:

```
JavaRDD<String> dGrafo = jsc.textFile("data/Gowalla.txt");	
JavaRDD<String> dNodi = dGrafo.map(x -> new String(x.split(",")[0],1)).reduceByKey((x,y)->x+y).map(x -> new String(x._1 + "," + x._1));
dNodi.saveAsTextFile("GowallaNodi");
```
Con il seguente codice, facendo uso della classe wrapper, abbiamo ottenuto la lista di archi tali da rendere il grafo indiretto:
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

1. **Preparazione dell'input con *Spark***:  
Dopo aver caricato il file `Gowalla.txt` sull'applicazione `ContaTriangoli.java`, inizia la parte di codice che ha lo scopo di produrre in output una lista in cui in ogni riga si trova il generico elemento {((u,v); d(u), d(v)}. 
Per fare ciò, abbiamo eseguito i seguenti passaggi:

| Passaggio        | Descrizione           |
|:---------- |:------------- |
| `Calcolo di:(NODO;GRADO)` | Al grafo diretto abbiamo applicato una funzione lambda che restituisce un oggetto in cui in chiave si trova il nodo in entrata, e in valore l'intero **1**; successivamente, mediante una `reduceByKey`, abbiamo ottenuto una lista in cui vi è in chiave il nodo, e in valore il suo grado; per semplicità, abbiamo poi convertito quest'ultimo in una stringa. |
| `Calcolo di:(ARCO;GRADI)` | Abbiamo poi creato due liste differenti in cui entrambe hanno come chiave l'arco, e come valore rispettivamente il grado del nodo in entrata e il grado del nodo in uscita. Con un join, intersecando per chiave le due liste, abbiamo ottenuto una lista in cui in chiave si trova l'arco, e in valore i gradi dei relativi nodi. Sempre per comodità, abbiamo poi convertito questo oggetto in una lista di stringhe. |


2. **Preparazione dell'input con *Neo4j***:
Dopo aver creato il grafo su *Neo4j*, abbiamo eseguito i seguenti passagi:

| Passaggio        | Descrizione           |
|:---------- |:------------- |
| `Calcolo di:(NODO;GRADO)` | Abbiamo eseguito una query per assegnare come attributo ad ogni nodo del grafo il relativo grado. Questa operazione è stata ottimizzata utilizzando il comando `node.degree()` della libreria `apoc`. | 
| `Calcolo di:(ARCO;GRADI)` | Per esportare la lista in cui il generico elemento è del tipo {(u,v); d(u), d(v)}, abbiamo dato come argomento del comando `export.csv.query()` della libreria `apoc` la query che permette di ottenere questo oggetto. Il file `ArcoGradi.csv` risultante sarà l'input del codice contenuto nel file `ContaTriangoli_NeoSpark.java`(per permetterne il caricamento su *GitHub* è stato suddiviso nei due file `ArcoGradi_pt1.txt` e `ArcoGradi_pt2.txt`).|


## Implementazione dell'algoritmo con *Java* e *Spark*
L'algoritmo, sia da un punto di vista operativo, sia da un punto di vista concettuale, è diviso in tre round. 

