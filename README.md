# Distributed-Download-Manager

Distributed Download Manager is a p2p command-line system which utilizes other computers which are connected locally to a partcular computer by distributing the load of 
downloading a large file to peer machines.It divides the large file in to defined
number of chunks according to the size of the file and send chunk boundaries
to peer computers in the same network, the peer computers will then download
the chunk and send it back to the computer that initialize the download. The
computer that initializes the download will download the file by itself if there
are no peers connected

## System Architecture
- Tracker server registers servers which have an internet connection and send active servers list when requested by clients.
- Server registers it self to the tracker and wait for client request. When request arrives it receives the chunk boundary of the file to be downloaded
and download the requested chunk only and stream it back to the client.
- Client fetchs the ip of active servers and distribute the chunks to all servers including it self, recieve chunks from servers assemble it to create the original file.It also assigns another server when a particular server fails to download a chunk.

## Tech Stack
  - [Picocli](https://picocli.info/)  for command line interface
  - [Log4j2](https://logging.apache.org/log4j/2.x/) for logging
  - [Maven](https://maven.apache.org/) for dependency management
  
## Setup
  - You need to have jdk installed in your pc and JAVA_HOME configured in enviroment variables.
  - Download [Maven](https://maven.apache.org/download.cgi) and configure M2_HOME in enviroment variables.
  - Clone this repository to your pc
  - Go to the cloned repo path using  `cd yo.ur-path/Distributed-Download-Manager`.
  - Run the following command `mvn compile assembly:single` then `mvn install`.
  - You will notice that target folder is created so you can now run the program using the command line.
    - use `java -jar "target/DDM.jar" tracker` to run the tracker.
    - use `java -jar "target/DDM.jar" server [tracker-ip]` to run the server.
    - use `java -jar "target/DDM.jar" client [tracker-ip] [download-url]` to run the client.
 

<p> <strong>Note</strong>: We strongly recommed that you run this system in separate computers.</p>

<p>This is a fun project and needs a lot of improvement so feel free to contribute</p> 
