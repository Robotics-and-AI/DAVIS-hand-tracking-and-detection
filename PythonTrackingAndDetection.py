""" 
----------------------------------
DAVIS HAND TRACKING AND DETECTION
----------------------------------
Author: Laura Duarte
"""

from collections import deque
import cv2
import glob
import math
import numpy as np
import os

class EventProcessor:

    """ 
    Methods for loading, processing and storing data
    """
    
    def __init__(self):

        # Event filter variables
        self.does_show_frames = True
        self.n_features = 5 # cluster center c (x,y) | cluster size s (x,y) | cluster density

        self._TpB = 20000 # time duration per event batch for tracking (in microseconds)
        self.grid_size = 30 # Size of detection areas (in pixels) 
        self._threshold_tracker = 100 # Minimum amount of events that need to be inside the cluster to activate tracking mode
        self._buffer_size_tracker = 500 # Number of events within the buffer

    # -------------------------- MAIN METHOD --------------------------
    
    def perform_tracking(self):

        """
        Create feature files from the .npy data files
        """

        # --- RUN FOR ALL NPY FILES ---
        npy_files = glob.glob(os.path.join("npyfiles","*.npy"),recursive=True)
        n_files = len(npy_files)

        for index, file in enumerate(npy_files):
            print(f"Analysing file {index+1}/{n_files}")

            x,y,ts = self._read_npy_file(file)

            # Show tracking and detection results for entire video
            file_features = self._get_features(np.array([x,y,ts],dtype = 'int64'))

    # -------------------------- AUXILIARY METHODS --------------------------

    def _get_features(self, events:list):
        
        """
        Process groups of events to get tracking features
        """

        # "events" is a list such that -> events = [x_coordinates, y_coordinates, ts_values]
        
        # - Allocate surfaces for tracking and detection -
        count_array_x = np.zeros(240)
        count_array_y = np.zeros(180)
        image = np.zeros((240,180))
        count_grid = np.zeros((round(240/self.grid_size),round(180/self.grid_size)))
        
        # - Initialize variables for tracking
        q_tracker = deque([])
        cluster = Cluster(self.grid_size, self._buffer_size_tracker)
        sum_valid_x = 0
        sum_valid_y = 0
        weight_sum = 0
        n_event_cluster = 0
        n_event_total = 0
        iterator = 0

        # - Get file length -
        n_timesteps = math.floor((events[2][-1]-events[2][0])/self._TpB)
        feature_array = np.zeros((n_timesteps,self.n_features),dtype=np.float32)

        for i in range(0,len(events[0][:])): # for each event of entire file

            # --- TRACKING SECTION ---
            # At each event

            e_x = events[0][i]
            e_y = events[1][i]
            e_ts = events[2][i]
            n_event_total += 1

            dist = cluster.distance(e_x,e_y)
            if (dist < cluster.search_radius):
                w = math.floor((count_array_x[e_x]+count_array_y[e_y])/2)
                sum_valid_x += w*e_x
                sum_valid_y += w*e_y
                weight_sum += w
                n_event_cluster += 1

            if len(q_tracker) > self._buffer_size_tracker:
                [old_x,old_y,old_grid_x,old_grid_y] = q_tracker.popleft()
                count_array_x[old_x] -= 1
                count_array_y[old_y] -= 1
                count_grid[old_grid_x][old_grid_y] -= 1
        
            count_array_x[e_x] += 1
            count_array_y[e_y] += 1
            image[e_x][e_y] += 1
            grid_x = math.floor(e_x/self.grid_size)
            grid_y = math.floor(e_y/self.grid_size)
            q_tracker.append([e_x,e_y,grid_x,grid_y])
            count_grid[grid_x][grid_y] += 1

            # Tracking function is called
            if (e_ts - events[2][0]) >= iterator*self._TpB:
                
                if iterator == n_timesteps:
                    break

                if weight_sum > 0:
                    avg_x = math.floor(sum_valid_x/weight_sum)
                    avg_y = math.floor(sum_valid_y/weight_sum)
                    dist = cluster.distance(avg_x,avg_y)
                    
                    if ((n_event_cluster < self._threshold_tracker and cluster.search_radius == cluster.min_cluster_radius) or (n_event_total < self._threshold_tracker)): # Detection            
                        cluster.detect(count_grid,self.grid_size)
                    
                    else: # Tracking
                        cluster.track(dist, avg_x, avg_y, count_array_x, count_array_y) # Tracking
                                            
                        if self.does_show_frames:
                            self.show_image(image, cluster.center_x, cluster.center_y, cluster.size_x, cluster.size_y)

                cluster.ts = e_ts
                if n_event_cluster > 0:
                    cluster.density = n_event_cluster/n_event_total
                else:
                    cluster.density = 0

                feature_array[iterator][:] = [cluster.center_x, cluster.center_y, cluster.size_x, cluster.size_y, cluster.density]

                # Reset tracking variables and iterate over next time duration
                image = np.zeros((240,180))
                sum_valid_x = 0
                sum_valid_y = 0
                weight_sum = 0
                n_event_cluster = 0
                n_event_total = 0
                iterator += 1

        return feature_array

    def show_image(self, frame:np.ndarray, cx, cy, sx, sy) -> None:
        
        """
        Show frame in given image_type (in gray)
        """
        frame = np.multiply(frame,(255/frame.max()))
        #frame = cv2.flip(frame,-1)
        frame8 = frame.astype(np.uint8)
        frame_final = cv2.applyColorMap(frame8, cv2.COLORMAP_JET)

        # Draw cluster
        start = (int(cy+sy/2),int(cx-sx/2)) # top left corner of rectangle
        end = (int(cy-sy/2),int(cx+sx/2)) # bottom right corner of rectangle
        color = (0,0,255) #BGR
        thickness = 2
        frame_final = cv2.rectangle(frame_final, start,end,color,thickness)

        cv2.imshow("frame",frame_final)
        cv2.waitKey(1)

    def _read_npy_file(self, file_name:str) -> list[list[int]]:

        """
        Read .npy file and output event data as x,y,ts,pol
        """
            
        try:
            event_data = np.load(file_name)
        except Exception:
            raise OSError("File not found.")

        x = event_data[0][:]
        y = event_data[1][:]
        ts = event_data[2][:]  
        ts = np.subtract(ts,ts[0])

        return x,y,ts #,pol
    
class Cluster:

    """ 
    Cluster object with properties and detection/tracking functions
    """
    
    def __init__(self, grid_size,buffer_size):

        self.density = 0
        self.grid_size = grid_size
        self.min_distance = 3
        self.max_count_scale = 3
        self.buffer_size = buffer_size
        self.min_cluster_radius = math.floor(self.grid_size/2)
        self.max_cluster_radius = 90 # Half of sensor height
        self.reset()

    def reset(self):
        
        self.disp_x = 0
        self.disp_y = 0
        self.detect_x = 0
        self.detect_y = 0
        self.size_x = 0
        self.size_y = 0
        self.search_radius = math.floor(self.grid_size/2)
        self.processing_mode = ""
        self.center_x = 120 #240/2
        self.center_y = 90 #180/2
        self.ts = 0

    def distance(self,ev_x:int, ev_y:int):
        
        ev_x -= self.center_x
        ev_y -= self.center_y
        return math.sqrt(ev_x*ev_x+ev_y*ev_y)
    
    def detect(self,count_grid,grid_size):
        
        self.processing_mode = "Detecting"
        self.vel_x = 0
        self.vel_y = 0
        self.disp_x = 0
        self.disp_y = 0

        max_grid = 0
        for i in range(len(count_grid)):
            for j in range(len(count_grid[0][:])):
                if (count_grid[i][j] > max_grid):
                    max_grid = count_grid[i][j]
                    self.detect_x = math.floor(grid_size/2) + i*grid_size
                    self.detect_y = math.floor(grid_size/2) + j*grid_size
        
        self.center_x = math.floor(0.8*self.center_x+0.2*self.detect_x)
        self.center_y = math.floor(0.8*self.center_y+0.2*self.detect_y)

    def track(self, distance, avg_x:int,avg_y:int, count_array_x, count_array_y):
        
        self.processing_mode = "Tracking"
        
        if (distance < self.min_distance):
            self.disp_x = 0
            self.disp_y = 0
        else:
            ratio_radius = math.floor(self.search_radius/2)
            alpha = math.exp((-distance*distance)/(2*ratio_radius*ratio_radius))

            self.disp_x = round(self.center_x * alpha + avg_x*(1-alpha) - self.center_x)
            self.disp_y = round(self.center_y * alpha + avg_y*(1-alpha) - self.center_y)

            self.center_x = self.center_x + self.disp_x
            self.center_y = self.center_y + self.disp_y
        
        # Calculation of zeroth moment mx and my
        min_x = max(0,self.center_x-self.search_radius)
        max_x = min(239, self.center_x+self.search_radius)
        min_y = max(0,self.center_y-self.search_radius)
        max_y = min(179, self.center_y+self.search_radius)

        mx = 0
        max_count_x = round(self.max_count_scale*(self.buffer_size/240))
        for i in range(min_x,max_x):
            mx = mx + min(count_array_x[i],max_count_x)

        my = 0
        max_count_y = round(self.max_count_scale*(self.buffer_size/180))
        for i in range(min_y,max_y):
            my = my + min(count_array_y[i],max_count_y)
        
        self.size_x = round(2*(mx/max_count_x))
        self.size_y = round(2*(my/max_count_y))
        radius_calc = round(max(self.size_x/2,self.size_y/2,self.min_cluster_radius))
        self.search_radius = round(min(radius_calc,self.max_cluster_radius))

if __name__ == "__main__":

    # --- INITIALIZE VARIABLES AND CLASSES ---
    event_processor = EventProcessor()

    # --- CREATE FEATURE DATA ---
    event_processor.perform_tracking()