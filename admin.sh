#!/bin/bash
cd admin
java -cp admin.jar:../server/server.jar dimcho.clientserver.AdminClient admin pass $*
