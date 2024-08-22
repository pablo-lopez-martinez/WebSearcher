# mri-websearcher

## Descripción

Este proyecto forma parte de la asignatura **Recuperación de la Información** del **Grado en Ingeniería Informática**. En esta práctica, se trabajará con la colección **TREC-COVID** para indexar documentos, 
realizar búsquedas, evaluar queries, y optimizar parámetros de modelos de recuperación.

## Funcionalidades

- **Indexación de la colección TREC-Covid**: A partir de los documentos `TREC-COVID` se parsean utilizando la librería `Jackson` y se indexan con los campos `id`, `title`, `test`, `url` y `pubmed_id`.
- **Búsqueda y evaluación de queries**: A través de un archivo de queries y unos juicios de relevancia que inidican si los documentos son relevantes, parcialmente relevantes o no relevantes se calcularán las métricas `P@n`, `Recall@n`, `RR` y `AP@n`.
- Visualizando para cada query el top m de documentos y para cada documento indice, score del documento y su juicio de relevancia además de sus métricas para cada query. Finalmente se mostrarán las métricas promediadas.
- **Entrenamiento y test**: Cuyo objetivo será encontrar el valor óptimo del parámetro de suavización del modelo basado en Language Models JM y de k1 para BM25, para un conjunto de queries de entrenamiento y de
- aplicar esos valores óptimos a un conjunto de queries test.
- **Significancia estadística**: Se realiza un test de significancia estadística sobre los resultados de dos modelos sobre las mismas queries de test.

## Configuración 

# IndexTrecCovid

`IndexTrecCovid` es una clase que se encarga de indexar la colección de documentos TREC-COVID utilizando Apache Lucene. Esta clase permite configurar diversos parámetros relacionados con la indexación, como el modo de apertura del índice y el modelo de recuperación de información.

### Argumentos

La clase `IndexTrecCovid` acepta los siguientes argumentos de línea de comandos:

- `-openmode <openmode>`: Especifica el modo de apertura del índice. Los valores posibles son:
  - `append`: Añade documentos a un índice existente sin eliminar los documentos anteriores.
  - `create`: Crea un nuevo índice, sobrescribiendo cualquier índice existente.
  - `create_or_append`: Crea un nuevo índice si no existe o añade documentos al índice existente.

- `-index <ruta>`: Define la ruta del directorio donde se almacenará o se encuentra el índice Lucene. Este argumento es obligatorio.

- `-docs <ruta>`: Especifica la ruta del directorio que contiene el corpus de documentos TREC-COVID (`corpus.jsonl`) y los archivos de consultas y juicios de relevancia (`queries.jsonl`, `test.tsv`). Este argumento es obligatorio.

- `-indexingmodel <modelo>`: Define el modelo de recuperación de información a utilizar durante la indexación. Los valores posibles son:
  - `jm <lambda>`: Utiliza el modelo de lenguaje con suavización de Jelinek-Mercer, donde `lambda` es el parámetro de suavización (un valor entre 0 y 1).
  - `bm25 <k1>`: Utiliza el modelo BM25, donde `k1` es el parámetro que ajusta la sensibilidad a la frecuencia del término. Para BM25, el valor por defecto de `b` es 0.75.

## SearchEvalTrecCovid

`SearchEvalTrecCovid` es una clase que se encarga de buscar y evaluar consultas en la colección de documentos TREC-COVID utilizando Apache Lucene. Esta clase permite configurar diversos parámetros relacionados con el modelo de recuperación de información, la ruta del índice, el corte en el ranking y la visualización de los resultados.

### Argumentos

La clase `SearchEvalTrecCovid` acepta los siguientes argumentos de línea de comandos:

- `-search <modelo>`: Especifica el modelo de recuperación de información a utilizar durante la búsqueda. Los valores posibles son:
  - `jm <lambda>`: Utiliza el modelo de lenguaje con suavización de Jelinek-Mercer, donde `lambda` es el parámetro de suavización (un valor entre 0 y 1).
  - `bm25 <k1>`: Utiliza el modelo BM25, donde `k1` es el parámetro que ajusta la sensibilidad a la frecuencia del término. Para BM25, el valor por defecto de `b` es 0.75.

- `-index <ruta>`: Define la ruta del directorio que contiene el índice Lucene. Este argumento es obligatorio.

- `-cut <n>`: Especifica el corte `n` en el ranking para el cálculo de las métricas de precisión (P), recall (R), y Average Precision (AP). Si no hay documentos relevantes en el corte `n` para una consulta, las métricas P, R y AP serán cero.

- `-top <m>`: Define cuántos documentos del ranking (top `m`) se deben visualizar. Los resultados se mostrarán en pantalla y se volcarán en un archivo de salida.

- `-queries <opción>`: Especifica las consultas a ejecutar y evaluar. Las opciones posibles son:
  - `all`: Ejecuta y evalúa todas las consultas del archivo.
  - `<int1>`: Ejecuta y evalúa solo la consulta identificada por el entero `int1`.
  - `<int1-int2>`: Ejecuta y evalúa el rango de consultas identificadas por los enteros `int1` a `int2`, ambos inclusive.

## TrainingTestTrecCovid

`TrainingTestTrecCovid` es una clase que se encarga de encontrar el valor óptimo de los parámetros de suavización para el modelo de lenguaje de Jelinek-Mercer (JM) y el parámetro `k1` para el modelo BM25, utilizando un conjunto de consultas de entrenamiento. Luego, aplica estos valores óptimos a un conjunto de consultas de prueba (test). La evaluación se realiza utilizando varias métricas de recuperación de información.

### Argumentos

La clase `TrainingTestTrecCovid` acepta los siguientes argumentos de línea de comandos:

- `-evaljm <int1-int2> <int3-int4>`: Ejecuta la optimización del parámetro `lambda` para el modelo de Jelinek-Mercer (JM). Las consultas en el rango `int1-int2` se usan para el entrenamiento, y las consultas en el rango `int3-int4` se usan para la evaluación. Este argumento es mutuamente excluyente con `-evalbm25`.

- `-evalbm25 <int1-int2> <int3-int4>`: Ejecuta la optimización del parámetro `k1` para el modelo BM25. Las consultas en el rango `int1-int2` se usan para el entrenamiento, y las consultas en el rango `int3-int4` se usan para la evaluación. Este argumento es mutuamente excluyente con `-evaljm`.

- `-cut <n>`: Especifica el corte `n` en el ranking para el cálculo de la métrica seleccionada durante el proceso de entrenamiento y evaluación.

- `-metrica <métrica>`: Define la métrica que se computará y optimizará durante el entrenamiento. Las opciones posibles son:
  - `P`: Precisión (Precision).
  - `R`: Recall.
  - `MRR`: Mean Reciprocal Rank.
  - `MAP`: Mean Average Precision.

- `-index <ruta>`: Define la ruta del directorio que contiene el índice Lucene. Este argumento es obligatorio.

## Compare

`Compare` es una clase que se encarga de realizar un test de significancia estadística para comparar los resultados de dos modelos de recuperación de información utilizando las mismas consultas de prueba. Esto permite determinar si las diferencias en rendimiento entre los dos modelos son estadísticamente significativas.

### Argumentos

La clase `Compare` acepta los siguientes argumentos de línea de comandos:

- `-test t | wilcoxon <alpha>`: Especifica el tipo de prueba de significancia estadística a realizar. Las opciones son:
  - `t`: Realiza un t-test para comparar los resultados de los dos modelos.
  - `wilcoxon`: Realiza la prueba de Wilcoxon Signed-Rank Test.
  - `<alpha>`: Especifica el nivel de significancia para la prueba estadística (por ejemplo, 0.05 para un nivel de significancia del 5%).

- `-results <results1.csv> <results2.csv>`: Especifica los dos archivos CSV que contienen los resultados de los modelos a comparar. Estos archivos deben haberse generado previamente utilizando la clase `TrainingTestTrecCovid`, y deben contener resultados para la misma métrica y las mismas consultas de prueba.
  - `<results1.csv>`: Ruta al primer archivo de resultados.
  - `<results2.csv>`: Ruta al segundo archivo de resultados.
