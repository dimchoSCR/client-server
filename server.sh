#!/bin/bash
cd server
java -cp server.jar -Djava.rmi.server.codebase=file:server/server.jar dimcho.clientserver.Server admin pass dimcho.clientserver.history.FileHistoryLogger