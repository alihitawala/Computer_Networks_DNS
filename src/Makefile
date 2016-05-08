JAVAC = javac
JAVACFLAGS =
SRC=edu/wisc/cs/sdn/simpledns/packet/DNSResourceRecord.java \
    edu/wisc/cs/sdn/simpledns/packet/DNS.java \
    edu/wisc/cs/sdn/simpledns/packet/DNSQuestion.java \
    edu/wisc/cs/sdn/simpledns/packet/DNSRdata.java \
    edu/wisc/cs/sdn/simpledns/packet/DNSRdataAddress.java \
    edu/wisc/cs/sdn/simpledns/packet/DNSRdataBytes.java \
    edu/wisc/cs/sdn/simpledns/packet/DNSRdataString.java \
    edu/wisc/cs/sdn/simpledns/packet/DNSRdataName.java \
	edu/wisc/cs/sdn/simpledns/Amazon.java \
	edu/wisc/cs/sdn/simpledns/SimpleDNS.java \
	edu/wisc/cs/sdn/simpledns/SimpleDNSHandler.java
CLS= $(SRC:.java=.class)
DNS=edu/wisc/cs/sdn/simpledns/packet/DNS.java
all:  $(CLS)

.SUFFIXES : .class .java
.java.class :
	$(JAVAC) $(JAVACFLAGS) $(SRC)

run: all
	java edu/wisc/cs/sdn/simpledns/SimpleDNS -r 198.41.0.4 -e ec2.csv

clean:
	rm -rf edu/wisc/cs/sdn/simpledns/packet/*.class edu/wisc/cs/sdn/simpledns/*.class
