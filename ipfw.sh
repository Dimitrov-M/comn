#!/bin/bash

delay='100ms'
plr='0.005'
bw='10Mbits/s'

ipfw flush

ipfw add pipe 100 in
ipfw add pipe 200 out
ipfw pipe 100 config delay $delay plr $plr bw $bw
ipfw pipe 200 config delay $delay plr $plr bw $bw
echo "delay: $delay"
echo "plr: $plr"
echo "bw: $bw"
