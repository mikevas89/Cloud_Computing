#!/bin/bash


rm headnode.jar
rm userCreator.jar
rm pingSender.jar

#create Jars
ant headnode-jar
ant client-jar
ant pingSender-jar


#send them to das4

echo "------------------"

printf "\n SENDING TO  DAS4...\n\n"

echo "------------------"

sshpass -p 'wantcloud1234' scp headnode.jar cld1467@fs3.das4.tudelft.nl:~/Lab/Implementation
sshpass -p 'wantcloud1234' scp userCreator.jar cld1467@fs3.das4.tudelft.nl:~/Lab/Implementation
sshpass -p 'wantcloud1234' scp pingSender.jar cld1467@fs3.das4.tudelft.nl:/var/scratch/cld1467/OpenNebula/

