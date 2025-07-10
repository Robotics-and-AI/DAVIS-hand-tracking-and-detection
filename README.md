# DAVIS Hand Tracking and Detection

## Introduction

Event-based sensors encode visual information asynchronously with low latency and high temporal resolution.  
This makes the event camera an ideal sensor to track fast movements such as hand gestures.  
This repository contributes with code to track and detect a human hand using a DAVIS240C event camera.  

## Deployment

The [Event-based Dataset of Assembly Tasks (EDAT24)](https://zenodo.org/records/10688518) is recommended as testing data (available in *.aedat* and *.npy* formats)

### jAER code

`TrackingAndDetection.java` - Implements both tracking and detection algorithms  
`TrackingAndDetectionUDP.java` - Extension of TrackingAndDetection to transmit tracking output through UDP  
`TrackingOnly.java` - Implements tracking algorithm  
`TrackingOnlyUDP.java` - Extension of TrackingOnly to transmit tracking output through UDP  

**Requirements:**  
- A <a href="https://docs.inivation.com/_static/hardware_guides/davis240.pdf">DAVIS240C event camera</a> **OR** DAVIS data in the *.aedat* format
- The <a href="http://jaerproject.org">jAER open-source software</a> to apply the tracking and detection algorithms on the event data

**Deployment:**  
1. Place the *.jaer* files from this repository into the *tracking* folder in the jAER software files `...\jAER\src\net\sf\jaer\eventprocessing\tracking`  
3. Build the jAER project in an Integrated Development Environment (e.g. NetBeans)  
4. Launch the (now updated) jAER software  
5. Access the jAER filter menu through `Ctrl+F` or `View → Show filters`  
6. In the `Select filters...` tab, move TrackingAndDetection, TrackingAndDetectionUDP, TrackingOnly and TrackingOnlyUDP to the `Selected classes` by using the `>` button
7. Connect the DAVIS240C event camera through USB **OR** open an *.aedat* file through `File → Open logged data file...`  
8. Return to the `Overview` tab and choose the desired tracking mode by selecting the corresponding box  
9. In the `Overview` tab, select `Controls` to alter the parameters of the corresponding tracking and detection algorithm as required  

---

### Python code

`PythonTrackingAndDetection.py` - Implements both tracking and detection algorithms  

**Requirements:**
- DAVIS data in the *.npy* format

**Deployment:**  
1. Create a folder structure as follows, with `PythonTrackingAndDetection.py` provided by this repository
```bash
└── Code
    ├── npyfiles
    │   └── ...
    └── PythonTrackingAndDetection.py
```
2. Insert the desired *.npy* DAVIS data files into the `...\Code\npyfiles` folder
3. Run the `PythonTrackingAndDetection.py` Python file
   - The algorithm will display the tracking results on a frame created through batching the event data in fixed time intervals
   - To alter the parameters of the tracking and detection algorithm as required, change the values in the *\_\_init\_\_* method of the *EventProcessor* Class
   - The tracking algorithm output can be accessed through the variable *file_features* inside the *perform_tracking* method

## Cite our paper
If you've found this work useful for your research, please cite our paper as follows

```
@article{Duarte2025,
author = {Duarte, L., Polito, M., Gastaldi, L., Pastorelli, S. and Neto, P},
doi = {-},
journal = {-},
title = {{Real-time human hand tracking and detection from event camera data}},
year = {2025}}
```




