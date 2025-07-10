
 package net.sf.jaer.eventprocessing.tracking;

 // Basic requirements
 import java.io.FileWriter;
 import java.io.IOException;
 import java.util.LinkedList;
 import java.util.Queue;
 
 // jAER dependencies
 import net.sf.jaer.Description;
 import net.sf.jaer.DevelopmentStatus;
 import net.sf.jaer.chip.AEChip;
 import net.sf.jaer.event.BasicEvent;
 import net.sf.jaer.event.ApsDvsEvent;
 import net.sf.jaer.event.EventPacket;
 import static net.sf.jaer.eventprocessing.EventFilter.log;
 import net.sf.jaer.eventprocessing.EventFilter2D;
 import net.sf.jaer.graphics.FrameAnnotater;
 import net.sf.jaer.util.DrawGL;
  
 // For graphical representation
 import com.jogamp.opengl.GL;
 import com.jogamp.opengl.GL2;
 import com.jogamp.opengl.GLAutoDrawable;
 import com.jogamp.opengl.util.gl2.GLUT;
 import java.awt.Color;
 

/**
 * Object tracker inspired by CAM-Shift approach
 * 
 * @author Laura Duarte
 */

@Description("Tracks a single moving object with stationary camera")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class TrackingAndDetection extends EventFilter2D implements FrameAnnotater {


    // 1. Global parameters
    private boolean writeToFile = getBoolean("writeToFile", false);
    private int eventThreshold = getInt("eventThreshold", 50);
    public enum DistanceMetric {Euclidean, Manhattan, Chebyshev} // Selection of distance metric
    private DistanceMetric distanceMetric = DistanceMetric.valueOf(getString("distanceMetric", DistanceMetric.Euclidean.toString()));
    private boolean showCluster = getBoolean("showCluster",true); // If cluster should be displayed or not
    
    // 2. Window Parameters
    private int bufferSize = getInt("bufferSize",1000); // Amount of events used to create event count map at each instance
    
    // Initialize Constants
    private final int minDistance = 3; // Minimum distance to perform tracking
    private final int initPosX = 120; // Initial position of cluster
    private final int initPosY = 90;
    private final int gridSize = 30; // Must be common divisor of sensor array size (60, 30, 15, 10,...)
    private final int maxCountScale = 3;
    private final String desktopPath = System.getProperty("user.home") + "/Desktop/";
    
    // Initialize Variables
    int fileCounter = 0;
    String fileName = null;
    float[] cyan = Color.getHSBColor(0.5f, 1f, 1f).getRGBComponents(null); // Window Color
    float[] red = Color.getHSBColor(1f, 1f, 1f).getRGBComponents(null);  // Search Color
    Window cluster = new Window(initPosX,initPosY); // Initialize cluster
    float sumValidX, sumValidY;
    int weightSum, nEvWindow, gridX, gridY;
    private int[] evCountX, evCountY; // Number of events at each x coordinate
    private int[][] evCountGrid; // Number of events at each y coordinate
    Queue<int[]> eventQueue = new LinkedList<>(); // Initialization of event queue
    
    // TrackingAndDetection class constructor
    public TrackingAndDetection(AEChip chip) {
        super(chip);
        this.chip = chip;
        setTooltips(); // Prepares gui
        resetPacketVars();
        resetGlobalVars();
    }
    
    protected final void setTooltips() {
        final String gl = "1. Global parameters", cl = "2. Cluster Parameters";
        
        // 1. Global parameters
        setPropertyTooltip(gl, "writeToFile", "Write statistics to .txt file in desktop");
        setPropertyTooltip(gl, "eventThreshold", "Threshold for cluster visibility");
        setPropertyTooltip(gl, "distanceMetric", "Choose the distance computation method");
        setPropertyTooltip(gl, "initPosX", "Initial centreX coordinate of cluster");
        setPropertyTooltip(gl, "initPosY", "Initial centreX coordinate of cluster");
        setPropertyTooltip(gl, "showCluster", "Displays clusters");
        // 2. Window Parameters
        setPropertyTooltip(cl, "bufferSize", "Amount of events used to create event count map at each instance");
    }
    
    @Override
    public void initFilter() {
    }  
    
    @Override
    synchronized public void resetFilter() {
        cluster = new Window(initPosX,initPosY);
        resetPacketVars();
        resetGlobalVars();
        fileCounter = fileCounter + 1;
        fileName = desktopPath + Integer.toString(fileCounter) + ".txt";       
        log.warning("TrackingAndDetection has been reset");
    }
    
    public final void resetPacketVars(){
        sumValidX = 0;
        sumValidY = 0;
        weightSum = 0;
        nEvWindow = 0;
    }
    
    public final void resetGlobalVars(){
        evCountX = new int[chip.getSizeX()]; // By default fills with zeros
        evCountY = new int[chip.getSizeY()];
        evCountGrid = new int[chip.getSizeX()/gridSize][chip.getSizeY()/gridSize];
        eventQueue.clear();
    }
    
    @Override
    public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        
        int nEvPacket = in.size;
        int x,y; // Coordinates event
        int time = 0; // Timestamp event
        int w; // Weight of event
        
        // Process new packet of events
        for(Object e : in){

            ApsDvsEvent i=(ApsDvsEvent)e;
            if(i.isDVSEvent()){ // Check that event is from DVS, not from APS
                x = i.x;
                y = i.y;
                time = i.timestamp;

                double dist = cluster.distance(x,y,distanceMetric.toString()); // Get distance from centre of cluster to event
                if(dist < cluster.windowRadius){
                    w = (int) (evCountX[x]+evCountY[y])/2;                 
                    sumValidX += w*x;
                    sumValidY += w*y;
                    weightSum += w;
                    nEvWindow += 1;
                }
                evCountX[x]++;
                evCountY[y]++;
                gridX = (int) Math.floor(x/gridSize);
                gridY = (int) Math.floor(y/gridSize);
                evCountGrid[gridX][gridY]++;
                eventQueue.add(new int[]{x,y,gridX,gridY});

                if (eventQueue.size() > bufferSize) {
                    int[] oldxy = eventQueue.remove();
                    evCountX[oldxy[0]]--;
                    evCountY[oldxy[1]]--;
                    evCountGrid[oldxy[2]][oldxy[3]]--;
                }
            }
        }
        
        if (weightSum > 0 && nEvPacket > eventThreshold){
            int avgX = (int)(sumValidX / weightSum);
            int avgY = (int)(sumValidY / weightSum);
            double dist = cluster.distance(avgX,avgY,distanceMetric.toString()); // Get distance from centre of cluster to average
            updateCluster(dist,avgX,avgY,time);
        }
        else{
            cluster.detect();
        }
        cluster.ts = time;
        resetPacketVars();
        return in;
    }

    protected void updateCluster(double dist,int avgX,int avgY,int time) {
        
        if(nEvWindow < eventThreshold && cluster.windowRadius == cluster.minWindowRadius){ // Detection
            cluster.detect();
        }
        else{ // Tracking           
            cluster.track(dist, avgX, avgY);
            
            // Write velocity statistics to created .txt file
            double deltaT = (time - cluster.ts)/1000;
            cluster.velX = cluster.dispX/deltaT;
            cluster.velY = cluster.dispY/deltaT;
            
            if ((writeToFile) && (fileName != null)) {
                try (FileWriter myWriter = new FileWriter(fileName,true)) {
                    myWriter.write(Double.toString(cluster.velX) +
                               ", " + Double.toString(cluster.velY) +
                               ", " + Double.toString(deltaT)+ "\n");
                } catch (IOException e) {
                    log.warning("An error occurred while writing to the .txt file.");}
            }
        }      
    }
    
    public class Window {

        int windowRadius = (int) Math.floor(gridSize/2); // Initial window radius size
        int windowSizeX, windowSizeY;
        int maxCountX, maxCountY;
        int minWindowRadius = (int) Math.floor(gridSize/2);
        int maxWindowRadius = gridSize;
        String processingMode = "";
        
        int centreX, centreY; // Window current centre
        int ts; // Last timestamp registered to window
        int dispX, dispY; // Window displacement
        float alphaXY;
        int mX, mY;
        int maxGrid, xDetect, yDetect;
        double velX, velY;

        // Window class constructor
        public Window(int xCentre, int yCentre){
            this.centreX = xCentre;
            this.centreY = yCentre;
            this.ts = 0;
        }

        public void detect(){
            
            processingMode = "Detecting";
            velX = 0;
            velY = 0;
            dispX = 0;
            dispY = 0;
            
            findMaxGrid(); // Update xDetect and yDetect
            centreX = (int) (0.8*centreX + 0.2*xDetect); //centreX + dispX;
            centreY = (int) (0.8*centreY + 0.2*yDetect); //centreY + dispY;
        }
        
        public void track(double distance,int avgX,int avgY){
            
            processingMode = "Tracking";
            
            findMaxGrid(); // Update xDetect and yDetect
            if (distance < minDistance){
                dispX = 0;
                dispY = 0;
            }
            else{
                // Gaussian low-pass filter - use half of the window radius as D0
                int ratioWindowRadius = (int) Math.floor(windowRadius/2);
                alphaXY = (float) Math.exp(-(distance*distance)/(2*ratioWindowRadius*ratioWindowRadius));
                alphaXY = (float)(alphaXY-0.2);
                
                dispX = (int)(centreX * alphaXY + avgX * (1-alphaXY) - centreX);
                dispY = (int)(centreY * alphaXY + avgY * (1-alphaXY) - centreY);
                //log.warning(String.format("%f, %d, %d", alphaXY, dispX, dispY));
                
                centreX = centreX + dispX;
                centreY = centreY + dispY;
            }
            
            // Calculation of zeroth moment mX and mY
            int minX = Math.max(0,centreX-windowRadius);
            int maxX = Math.min(chip.getSizeX()-1,centreX+windowRadius);
            int minY = Math.max(0,centreY-windowRadius);
            int maxY = Math.min(chip.getSizeY()-1,centreY+windowRadius);

            mX = 0;
            maxCountX = (int) maxCountScale*(bufferSize/chip.getSizeX());
            for(int i= minX; i<=maxX; i++){ // Calculate first moment of X inside window
                mX += Math.min(evCountX[i],maxCountX);}

            mY = 0;
            maxCountY = (int) maxCountScale*(bufferSize/chip.getSizeY());
            for(int i= minY; i<=maxY; i++){ // Calculate first moment of Y inside window
                mY += Math.min(evCountY[i],maxCountY);}

            windowSizeX = (int) 2*(mX/maxCountX); // Update X cluster size
            windowSizeY = (int) 2*(mY/maxCountY); // Update Y cluster size
            int windowRadiusCalc = (int) Math.max(windowSizeX/2, Math.max(windowSizeY/2, minWindowRadius)); // Update cluster size
            windowRadius = (int) Math.min(windowRadiusCalc,maxWindowRadius);
        }
        
        public void findMaxGrid(){
            maxGrid = 0;            
            for (int i=0; i < evCountGrid.length; i++){ //Find best grid candidate
                for (int j = 0; j < evCountGrid[i].length;j++){
                    if (evCountGrid[i][j] > maxGrid){
                        maxGrid = evCountGrid[i][j];
                        xDetect = ((int) Math.floor(gridSize/2)) + i*gridSize;
                        yDetect = ((int) Math.floor(gridSize/2)) + j*gridSize;
                    }
                }
            }
        }
        
        public void draw(GLAutoDrawable drawable) {
            GL2 gl = drawable.getGL().getGL2();
            final GLUT glut = new GLUT();
            final float BOX_LINE_WIDTH = 2f;
                
            // Set color and line width of cluster annotation
            if ("Tracking".equals(cluster.processingMode)){
                gl.glPushMatrix();
                gl.glTranslatef(centreX, centreY, 0); // Center everything -> origin = 0
                DrawGL.drawVector(gl, 0, 0, cluster.dispX, cluster.dispY, 3, 10); // Velocity vector
                
                // Draw rectangle-shaped cluster
                gl.glLineWidth(BOX_LINE_WIDTH);
                gl.glColor3fv(cyan, 0);
                DrawGL.drawBox(gl, 0, 0, (int) windowSizeX, (int) windowSizeY, 0); // Actual window
                gl.glColor3fv(red, 0);
                DrawGL.drawBox(gl, 0, 0, (int) windowRadius*2, (int) windowRadius*2, 0); // Search area
                gl.glPopMatrix();

                // Draw centre point
                gl.glColor3fv(cyan, 0);
                gl.glPointSize(16f);
                gl.glBegin(GL.GL_POINTS);
                gl.glVertex2f(cluster.centreX, cluster.centreY); //Centre of cluster
                gl.glEnd();
            }
            else{ //Detection
                // Draw search point
                gl.glColor3fv(red, 0);
                gl.glPointSize(16f);
                gl.glBegin(GL.GL_POINTS);
                gl.glVertex2f(cluster.xDetect, cluster.yDetect); //Detection estimate
                gl.glEnd();
            }
            
            // Write current processing mode
            gl.glPushMatrix();
            gl.glColor3f(.9f, .9f, .9f);        
            gl.glRasterPos3f(5, 5, 0); 
            String s = String.format("Processing Mode: %s",cluster.processingMode);
            glut.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_24, s);      
            gl.glPopMatrix();
            
        }

        public double distance(int xEv,int yEv,String method){
            
            xEv -= this.centreX;
            yEv -= this.centreY;

            return (switch (method) {
                case "Euclidean" -> Math.sqrt(xEv * xEv + yEv * yEv);
                case "Manhattan" -> Math.abs(xEv) + Math.abs(yEv);
                case "Chebyshev" -> Math.max(Math.abs(xEv), Math.abs(yEv));
                default -> Math.sqrt(xEv * xEv + yEv * yEv);
            });
        }
    }
    
    // 1. Global parameters
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --writeToFile-">
    public boolean getwriteToFile () {
        return writeToFile;
    }
    public void setwriteToFile(boolean writeToFile) {
        this.writeToFile = writeToFile;
        putBoolean("writeToFile", writeToFile);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --eventThreshold-">
    public int geteventThreshold() {
        return eventThreshold;
    }
    public void seteventThreshold(int eventThreshold) {
        if (eventThreshold < 1){eventThreshold = 1;}
        if (eventThreshold > 1000){eventThreshold = 1000;}
        this.eventThreshold = eventThreshold;
        putInt("eventThreshold", eventThreshold);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --showCluster-">
    public boolean getshowCluster () {
        return showCluster;
    }
    public void setshowCluster(boolean showCluster) {
        this.showCluster = showCluster;
        putBoolean("showCluster", showCluster);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --distanceMetric-">

    public DistanceMetric getdistanceMetric() {
        return distanceMetric;
    }

    public void setdistanceMetric(DistanceMetric distanceMetric) {
        log.warning(String.format("New distance metric was set: %s",distanceMetric));
        this.distanceMetric = distanceMetric;
        putString("distanceMetric", this.distanceMetric.toString());
    }
    // </editor-fold>
    
    // 2. Window Parameters 
    // <editor-fold defaultstate="collapsed" desc="getter-setter for --bufferSize-">
    public int getbufferSize() {
        return bufferSize;
    }
    public void setbufferSize(int bufferSize) {
        if (bufferSize < 100){bufferSize = 100;}
        if (bufferSize > 10000){bufferSize = 10000;}
        eventQueue.clear(); //otherwise, when reducing bufferSize, the original buffer "size" remains
        this.bufferSize = bufferSize;
        putInt("bufferSize", bufferSize);
    }
    // </editor-fold>
    
    @Override
    public void annotate(GLAutoDrawable drawable) {
        
        if (!showCluster) {
            return;
        }
        
        GL2 gl = drawable.getGL().getGL2();
        if (gl == null) {
            log.warning("null GL in RectangularClusterTracker.annotate");
            return;
        }
        
        // Draw cluster
        try {
            gl.glPushMatrix();
            cluster.draw(drawable);
        } catch (java.util.ConcurrentModificationException e) {
            // if cluster is modified by real time filter during rendering of cluster
            log.warning("Concurrent modification of cluster list while drawing cluster");
        } finally {
            gl.glPopMatrix();
        }
    }
}


