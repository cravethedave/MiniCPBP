#!/bin/bash
#SBATCH --time=00:01:00
cd /home/dsaikali/projects/def-pesantg/dsaikali/minicpbp
module load java
module load maven
mvn exec:java -Dexec.mainClass="minicpbp.examples.FMMTest"