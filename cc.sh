#!/bin/bash
#SBATCH --time=00:01:00
cd /home/dsaikali/projects/def-pesantg/dsaikali/minicpbp
mvn exec:java -Dexec.mainClass="minicpbp.examples.FMMTest"