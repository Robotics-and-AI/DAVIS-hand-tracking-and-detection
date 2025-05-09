/*
 * eCAMShiftUDP.java
 *
 * Created on May 14, 2024, 12:36 AM
 */

 package net.sf.jaer.eventprocessing.tracking;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.logging.Level;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import static net.sf.jaer.eventprocessing.EventFilter.log;


/**
 * Object tracker that sends cluster info to remote host about tracked clusters
 */
 
@Description("Object tracker that sends cluster info to remote host about tracked clusters")
public class eCAMShiftUDP extends eCAMShift {

    protected DatagramChannel channel = null;
    protected DatagramSocket socket = null;
    protected String host = "192.168.56.108"; //"192.168.56.108"
    protected int port = getInt("port", 65432);
    InetSocketAddress client = null;
 
    public eCAMShiftUDP(AEChip chip) {
        super(chip);
        String s="  remote host";
        setPropertyTooltip(s, "port", "port to send to on remote host");
        setPropertyTooltip(s, "host", "host to send to");
        try {
            channel = DatagramChannel.open();
        socket = channel.socket(); // bind to any available port because we will be sending datagrams with included host:port info
        } catch (IOException ex) {
            log.log(Level.WARNING, "cannot open channel {0}", ex.toString());
        }
    }

    @Override
    synchronized public void resetFilter() {
        super.resetFilter();
        try {
            channel = DatagramChannel.open();
        socket = channel.socket(); // bind to any available port because we will be sending datagrams with included host:port info
        } catch (IOException ex) {
            log.log(Level.WARNING, "cannot open channel {0}", ex.toString());
        }
    }
    
    @Override
    protected void updateCluster(double dist,int avgX,int avgY,int time, int nEvPacket) {
        super.updateCluster(dist,avgX,avgY,time,nEvPacket);
        //log.warning(String.format("%f,%d,%d,%d", dist, avgX,avgY,time));
        if(channel == null){
            log.warning("No channel to send on");
            return;
        }
        checkClient();
        ByteBuffer b = makeDatagram(cluster);
        //log.warning(String.format("%h", b));
        try {
            channel.send(b,client);
        } catch (IOException ex) {
            log.warning(ex.toString());
        }
    }
    
    ByteBuffer makeDatagram(Window cluster){
        ByteBuffer b = ByteBuffer.allocate(16);
        b.order(ByteOrder.LITTLE_ENDIAN); // check this for actual reciever
        //log.warning(String.format("%f,%f", cluster.velX, cluster.velY));
        b.putDouble(-cluster.velX);
        b.putDouble(cluster.velY);
        b.flip();
        //b.putLong(System.currentTimeMillis());
        return b;
    }
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --Host-">
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --Port-">

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
        putInt("port", port);
    }
    // </editor-fold>
    
    // returns true if socket exists and is bound
    private boolean checkClient (){
        if ( socket == null ){
            return false;
        }
        try{
            if (socket.isBound()){
                return true;
            }
            client = new InetSocketAddress(host,port);
            return true;
        } catch (Exception se){ // IllegalArgumentException or SecurityException
            log.log(Level.WARNING, "While checking client host={0} port={1} caught {2}", new Object[]{host, port, se.toString()});
            return false;
        }
    }
}
