# ROTOSCOPING, CAMERA TRACKING & OBJECT TRACKING: THE DEFINITIVE A-Z GUIDE

> **AIDITOR Advanced Video Engineering Specification**  
> **Document Version**: 1.0.0 (Deep Research Edition)  
> **Target Platforms**: Android (ARM64-v8a / NDK / MediaCodec / OpenGL ES 3.0 / Kotlin Compose) & Standalone Python Engine  
> **Benchmark Applications**: CapCut (ByteDance), Node Video, TrackIt Motion, Mocha Pro, After Effects 3D Camera Tracker

---

## Table of Contents
1. [Executive Summary & Mobile State of the Art](#1-executive-summary--mobile-state-of-the-art)
2. [2D Subject & Object Tracking](#2-2d-subject--object-tracking)
   - [2.1 Classical Discriminative Correlation Filters (MOSSE, KCF, CSRT)](#21-classical-discriminative-correlation-filters-mosse-kcf-csrt)
   - [2.2 Deep Siamese Tracking Networks (SiamFC, SiamRPN)](#22-deep-siamese-tracking-networks-siamfc-siamrpn)
   - [2.3 Group Point Tracking (CoTracker & TAP-Vid)](#23-group-point-tracking-cotracker--tap-vid)
   - [2.4 Bounding Box Tracking & Temporal Smoothing (Kalman Filter & Hungarian)](#24-bounding-box-tracking--temporal-smoothing-kalman-filter--hungarian)
   - [2.5 Target-Locked Video Stabilization (Face Lock / Object Lock)](#25-target-locked-video-stabilization-face-lock--object-lock)
3. [Planar Tracking & Homography](#3-planar-tracking--homography)
   - [3.1 The 3x3 Projective Homography Matrix](#31-the-3x3-projective-homography-matrix)
   - [3.2 4-Point Corner Pinning (Screen & Surface Replacement)](#32-4-point-corner-pinning-screen--surface-replacement)
   - [3.3 Inverse Compositional Image Alignment (Baker-Matthews)](#33-inverse-compositional-image-alignment-baker-matthews)
4. [3D Camera Tracking (MatchMoving / Visual SLAM)](#4-3d-camera-tracking-matchmoving--visual-slam)
   - [4.1 Epipolar Geometry & The Essential Matrix](#41-epipolar-geometry--the-essential-matrix)
   - [4.2 5-Point and 8-Point Solvers with RANSAC](#42-5-point-and-8-point-solvers-with-ransac)
   - [4.3 Camera Pose Recovery (R, t via SVD Decomposition)](#43-camera-pose-recovery-r-t-via-svd-decomposition)
   - [4.4 3D Point Cloud Triangulation & Bundle Adjustment](#44-3d-point-cloud-triangulation--bundle-adjustment)
   - [4.5 Ground Plane Detection & 3D Text Pinning with Parallax](#45-ground-plane-detection--3d-text-pinning-with-parallax)
5. [AI Video Rotoscoping & Human Matting](#5-ai-video-rotoscoping--human-matting)
   - [5.1 The Alpha Matting Equation & Trimap Problem](#51-the-alpha-matting-equation--trimap-problem)
   - [5.2 RobustVideoMatting (RVM - ByteDance/CapCut WACV 2022)](#52-robustvideomatting-rvm---bytedancecapcut-wacv-2022)
   - [5.3 SAM 2: Segment Anything in Images and Videos (Meta AI 2024)](#53-sam-2-segment-anything-in-images-and-videos-meta-ai-2024)
   - [5.4 Google MediaPipe On-Device Selfie Segmentation](#54-google-mediapipe-on-device-selfie-segmentation)
   - [5.5 Neon Saber / Edge Bloom Shader Synthesis](#55-neon-saber--edge-bloom-shader-synthesis)
6. [Mobile Implementation Blueprint for AIDITOR Android App](#6-mobile-implementation-blueprint-for-aiditor-android-app)
   - [6.1 Real-Time UI Preview vs Offline Render Engine](#61-real-time-ui-preview-vs-offline-render-engine)
   - [6.2 Jetpack Compose Inspector Sheet & Touch Interaction](#62-jetpack-compose-inspector-sheet--touch-interaction)
   - [6.3 OpenGL ES 3.0 / MediaCodec GPU Shader Pipeline](#63-opengl-es-30--mediacodec-gpu-shader-pipeline)
7. [Academic & Technical References](#7-academic--technical-references)

---

## 1. Executive Summary & Mobile State of the Art

Modern mobile video editing applications such as **CapCut (ByteDance)**, **Node Video**, and **TrackIt Motion** have revolutionized mobile content creation by bringing features that formerly required desktop VFX suites (After Effects, Mocha Pro, Nuke, DaVinci Resolve) into intuitive touch interfaces.

These features fall into three major technical domains:
1. **Object & Subject Tracking**: Tracking 2D coordinates, scale, and rotation of a user-selected target (person, face, ball, vehicle) to anchor floating text, stickers, blur masks, or lock the camera viewport onto the subject (Target Lock / Face Lock).
2. **Planar & 3D Camera Tracking**: Computing perspective homographies or solving the 3D camera trajectory ($K[R|t]$) from natural scene features to anchor elements (3D floating typography, logos, posters) into the physical 3D environment with authentic parallax.
3. **AI Rotoscoping & Matting**: Isolating foreground subjects from complex backgrounds without green screens, generating temporal-consistent alpha masks ($\alpha$), and applying stylized effects such as the iconic **Neon Saber**, outline glow, background blur, or behind-subject text compositing.

---

## 2. 2D Subject & Object Tracking

```
┌─────────────────┐       ┌────────────────────────┐       ┌──────────────────────┐
│ Initial Target  │ ────► │ Discriminative Feature │ ────► │ Correlation Response │
│  Bounding Box   │       │ Extraction (HOG/Color) │       │  Peak Detection      │
└─────────────────┘       └────────────────────────┘       └──────────┬───────────┘
                                                                      │
                                                                      ▼
┌─────────────────┐       ┌────────────────────────┐       ┌──────────────────────┐
│ Target Lock     │ ◄──── │ Temporal Smoothing     │ ◄──── │ Sub-Pixel Peak       │
│ Stabilization   │       │ (Kalman / Low-Pass)    │       │ Interpolation        │
└─────────────────┘       └────────────────────────┘       └──────────────────────┘
```

### 2.1 Classical Discriminative Correlation Filters (MOSSE, KCF, CSRT)

#### Minimum Output Sum of Squared Error (MOSSE, Bolme et al., CVPR 2010)
MOSSE models tracking as finding an optimal correlation filter $H$ that maps input image patches $F$ to desired Gaussian response outputs $G$:

$$\min_{H^*} \sum_i |F_i \odot H^* - G_i|^2$$

In the frequency domain (via 2D Fast Fourier Transform $\mathcal{F}$):

$$H^* = \frac{\sum_i G_i \odot F_i^*}{\sum_i F_i \odot F_i^* + \epsilon}$$

Where $\odot$ represents element-wise multiplication, $*$ denotes complex conjugate, and $\epsilon$ is a regularization parameter to avoid division by zero. MOSSE operates at over **450 FPS** on mobile CPUs, making it ideal for ultra-low-power tracking.

#### Kernelized Correlation Filters (KCF, Henriques et al., TPAMI 2015)
KCF exploits the circulant matrix structure of shifted image samples. Because cyclic shifts correspond to diagonalized matrices in the Fourier domain:

$$\hat{\alpha} = \frac{\hat{y}}{\hat{k}^{xx} + \lambda}$$

Where $\hat{k}^{xx}$ is the Fourier transform of the kernel autocorrelation (e.g. Gaussian RBF kernel $k(x, x') = \exp\left(-\frac{1}{\sigma^2} (\|x\|^2 + \|x'\|^2 - 2 \mathcal{F}^{-1}(\hat{x}^* \odot \hat{x}'))\right)$). KCF incorporates multi-channel HOG (Histogram of Oriented Gradients) features and runs at 150+ FPS on ARM CPUs.

#### Channel and Spatial Reliability Tracking (CSRT, Lukežič et al., CVPR 2017)
CSRT addresses non-rectangular and deformed objects by learning an explicit spatial reliability map $m \in \{0, 1\}^{W \times H}$ using graph cuts on color histograms:

$$\min_{h, m} \sum_{c=1}^{D} \left\| f_c \star (m \odot h_c) - g \right\|^2 + \lambda \sum_{c=1}^D \|h_c\|^2$$

CSRT evaluates channel reliability weights $w_c$ based on discriminative peak-to-sidelobe ratio (PSR):

$$w_c = \frac{R_{\max} - \mu_R}{\sigma_R}$$

CSRT delivers the highest tracking accuracy among classical CPU-based trackers, with robust handling of rotation and aspect-ratio changes.

### 2.2 Deep Siamese Tracking Networks (SiamFC, SiamRPN)
- **SiamFC (Bertinetto et al., ECCV 2016)**: Computes a cross-correlation between the template patch $z$ and search region $x$ in deep feature space:
  $$f(z, x) = \varphi(z) \star \varphi(x) + b \mathbb{1}$$
- **SiamRPN (Li et al., CVPR 2018)**: Adds a Region Proposal Network (RPN) with classification and bounding-box regression branches, dynamically predicting $(dx, dy, dw, dh)$ offsets.

### 2.3 Group Point Tracking (CoTracker & TAP-Vid)
Introduced by Meta AI (*CoTracker: It is Better to Track Together*, arXiv:2307.07635):
- Traditional point tracking (e.g., KLT) tracks each point independently, leading to drift under occlusion.
- **CoTracker Innovation**: Employs a Transformer architecture with cross-attention across multiple point trajectories ($N$ points jointly tracked across sliding temporal windows).
- Tracks up to **70,000 points simultaneously**, maintaining track continuity through occlusions and reappearance by attending to the motion of visible correlated points on the same rigid or non-rigid body.

### 2.4 Bounding Box Tracking & Temporal Smoothing (Kalman Filter & Hungarian)
When tracking subjects over extended sequences, jitter occurs due to discretization noise. A continuous Kalman filter models the state vector:

$$\mathbf{x}_k = [x_c, y_c, s, r, \dot{x}_c, \dot{y}_c, \dot{s}]^T$$

Where $(x_c, y_c)$ is the center coordinate, $s$ is scale (bounding box area $w \times h$), and $r$ is the aspect ratio $w/h$.
- **Prediction**: $\mathbf{x}_{k|k-1} = \mathbf{F} \mathbf{x}_{k-1|k-1}$, $\mathbf{P}_{k|k-1} = \mathbf{F} \mathbf{P}_{k-1|k-1} \mathbf{F}^T + \mathbf{Q}$.
- **Update**: $\mathbf{K}_k = \mathbf{P}_{k|k-1} \mathbf{H}^T (\mathbf{H} \mathbf{P}_{k|k-1} \mathbf{H}^T + \mathbf{R})^{-1}$, $\mathbf{x}_{k|k} = \mathbf{x}_{k|k-1} + \mathbf{K}_k (\mathbf{z}_k - \mathbf{H} \mathbf{x}_{k|k-1})$.

### 2.5 Target-Locked Video Stabilization (Face Lock / Object Lock)
CapCut's popular "Lock Face" or "Lock Object" effect anchors the tracked subject at the center of the video screen while the camera perspective dynamically re-centers around it.

```
       Original Frame                           Stabilized & Target-Locked Frame
┌───────────────────────────────┐              ┌───────────────────────────────┐
│                               │              │               ▲               │
│         [ Tracked ]           │   Affine     │               │ (Zoomed)      │
│         [  Face   ]           │ ──────────►  │        ┌──────────────┐       │
│           (x_c, y_c)          │ Transform    │        │ Tracked Face │       │
│                               │              │        └──────────────┘       │
│                               │              │        Pinned to (0.5, 0.5)   │
└───────────────────────────────┘              └───────────────────────────────┘
```

The transformation matrix $M_{\text{lock}}(t)$ maps any pixel $\mathbf{p} = [x, y, 1]^T$ in frame $t$ to stabilized canvas coordinates:

$$M_{\text{lock}}(t) = \begin{bmatrix} S(t) \cos\theta(t) & -S(t) \sin\theta(t) & \Delta x(t) \\ S(t) \sin\theta(t) & S(t) \cos\theta(t) & \Delta y(t) \\ 0 & 0 & 1 \end{bmatrix}$$

Where:
- $\Delta x(t) = 0.5 \cdot W_{\text{canvas}} - S(t) \cdot x_c(t)$
- $\Delta y(t) = 0.5 \cdot H_{\text{canvas}} - S(t) \cdot y_c(t)$
- $S(t) = \frac{S_{\text{target}}}{s(t)}$ (compensates for subject moving closer or farther)
- $\theta(t) = -\theta_{\text{subject}}(t)$ (compensates for head/body tilt)

---

## 3. Planar Tracking & Homography

Planar tracking tracks 2D planar surfaces (billboards, computer screens, walls, floors, vehicle doors) across perspective distortions.

### 3.1 The 3x3 Projective Homography Matrix
A homography $H \in \mathbb{R}^{3 \times 3}$ relates coplanar points between frame $t_0$ and frame $t_1$ up to scale:

$$\begin{bmatrix} x' \\ y' \\ 1 \end{bmatrix} \sim \begin{bmatrix} h_{11} & h_{12} & h_{13} \\ h_{21} & h_{22} & h_{23} \\ h_{31} & h_{32} & h_{33} \end{bmatrix} \begin{bmatrix} x \\ y \\ 1 \end{bmatrix} \quad \implies \quad x' = \frac{h_{11}x + h_{12}y + h_{13}}{h_{31}x + h_{32}y + h_{33}}, \quad y' = \frac{h_{21}x + h_{22}y + h_{23}}{h_{31}x + h_{32}y + h_{33}}$$

$H$ has 8 degrees of freedom ($h_{33} = 1$). Each point correspondence $(x_i, y_i) \leftrightarrow (x'_i, y'_i)$ provides two independent linear equations:

$$\begin{bmatrix} -x_i & -y_i & -1 & 0 & 0 & 0 & x_i x'_i & y_i x'_i & x'_i \\ 0 & 0 & 0 & -x_i & -y_i & -1 & x_i y'_i & y_i y'_i & y'_i \end{bmatrix} \mathbf{h} = \mathbf{0}$$

Four non-collinear point correspondences yield an exact solution solved via Singular Value Decomposition (Direct Linear Transformation - DLT).

### 3.2 4-Point Corner Pinning (Screen & Surface Replacement)
In mobile video editing (Node Video / TrackIt Motion):
1. The user places 4 corner pins: $P_1(\text{TL}), P_2(\text{TR}), P_3(\text{BR}), P_4(\text{BL})$ on the screen/surface to be replaced.
2. The tracker computes $H(t)$ across frames using optical flow patch alignment.
3. The replacement image/video (e.g. $[0,0] \to [w_r, h_r]$) is warped onto the video stream using an inverse projective shader:
   $$\mathbf{x}_{\text{src}} = H(t)^{-1} \mathbf{x}_{\text{dest}}$$

### 3.3 Inverse Compositional Image Alignment (Baker-Matthews, IJCV 2004)
Direct image alignment minimizes the sum of squared differences (SSD) over all pixels within the planar region $\Omega$:

$$\min_{\Delta \mathbf{p}} \sum_{\mathbf{x} \in \Omega} \left[ T(W(\mathbf{x}; \Delta \mathbf{p})) - I(W(\mathbf{x}; \mathbf{p})) \right]^2$$

In the Inverse Compositional algorithm, the warp update $\Delta \mathbf{p}$ is solved with respect to the template $T$ rather than the current frame $I$. The Gauss-Newton update equation:

$$\Delta \mathbf{p} = H_{\text{approx}}^{-1} \sum_{\mathbf{x} \in \Omega} \left[ \nabla T \frac{\partial W}{\partial \mathbf{p}} \right]^T \left[ I(W(\mathbf{x}; \mathbf{p})) - T(\mathbf{x}) \right]$$

Because $\nabla T$ and the Jacobian $\frac{\partial W}{\partial \mathbf{p}}$ depend **only** on the static reference template, the Hessian matrix:

$$H_{\text{approx}} = \sum_{\mathbf{x} \in \Omega} \left[ \nabla T \frac{\partial W}{\partial \mathbf{p}} \right]^T \left[ \nabla T \frac{\partial W}{\partial \mathbf{p}} \right]$$

is precomputed and inverted **once**, enabling real-time planar tracking on mobile devices.

---

## 4. 3D Camera Tracking (MatchMoving / Visual SLAM)

```
        Natural Video Frames
                 │
                 ▼
    ┌──────────────────────────┐
    │ 2D Feature Extraction    │ (Shi-Tomasi / ORB / FAST)
    │ & KLT Temporal Tracking  │
    └────────────┬─────────────┘
                 │
                 ▼
    ┌──────────────────────────┐
    │ 5-Point Essential Matrix │ (Nistér Solver + RANSAC)
    │ Estimation (E = [t]x R)  │
    └────────────┬─────────────┘
                 │
                 ▼
    ┌──────────────────────────┐
    │ SVD Pose Decomposition   │ (Camera Rotation R_k, Translation t_k)
    │ & 3D Triangulation       │
    └────────────┬─────────────┘
                 │
                 ▼
    ┌──────────────────────────┐
    │ Ground Plane Estimation  │ (RANSAC Plane Fitting)
    │ & 3D Parallax Pinning    │
    └──────────────────────────┘
```

### 4.1 Epipolar Geometry & The Essential Matrix
For a calibrated camera with intrinsic calibration matrix $K$:

$$K = \begin{bmatrix} f_x & 0 & c_x \\ 0 & f_y & c_y \\ 0 & 0 & 1 \end{bmatrix}$$

Normalized image coordinates $\mathbf{x} = K^{-1} \mathbf{p}$ between two views satisfy the Epipolar Constraint:

$$\mathbf{x}_2^T \mathbf{E} \mathbf{x}_1 = 0$$

Where $\mathbf{E} \in \mathbb{R}^{3 \times 3}$ is the **Essential Matrix**:

$$\mathbf{E} = [\mathbf{t}]_\times \mathbf{R} = \begin{bmatrix} 0 & -t_z & t_y \\ t_z & 0 & -t_x \\ -t_y & t_x & 0 \end{bmatrix} \mathbf{R}$$

### 4.2 5-Point and 8-Point Solvers with RANSAC
- **8-Point Algorithm (Longuet-Higgins, 1981)**: Solves $\mathbf{E}$ linearly from 8 point correspondences via SVD on $A \mathbf{e} = 0$.
- **5-Point Algorithm (Nistér, PAMI 2004)**: Exploits the two algebraic constraints of the Essential Matrix:
  1. $\det(\mathbf{E}) = 0$
  2. $2 \mathbf{E} \mathbf{E}^T \mathbf{E} - \text{tr}(\mathbf{E} \mathbf{E}^T) \mathbf{E} = 0$
  Solves a 10th-degree polynomial to extract up to 10 candidate solutions from just 5 points.
- **RANSAC (Random Sample Consensus)**: Randomly samples minimal sets (5 points), computes candidate $\mathbf{E}$, counts inliers with Sampson distance:
  $$d_{\text{Sampson}}^2 = \frac{(\mathbf{x}_2^T \mathbf{E} \mathbf{x}_1)^2}{(\mathbf{E} \mathbf{x}_1)_1^2 + (\mathbf{E} \mathbf{x}_1)_2^2 + (\mathbf{E}^T \mathbf{x}_2)_1^2 + (\mathbf{E}^T \mathbf{x}_2)_2^2}$$

### 4.3 Camera Pose Recovery (R, t via SVD Decomposition)
Given the optimal Essential matrix $\mathbf{E}$, compute SVD: $\mathbf{E} = \mathbf{U} \mathbf{\Sigma} \mathbf{V}^T$, where $\mathbf{\Sigma} = \text{diag}(1, 1, 0)$. Defining:

$$\mathbf{W} = \begin{bmatrix} 0 & -1 & 0 \\ 1 & 0 & 0 \\ 0 & 0 & 1 \end{bmatrix}$$

Four possible solutions exist:
1. $\mathbf{R}_1 = \mathbf{U} \mathbf{W} \mathbf{V}^T, \quad \mathbf{t}_1 = +\mathbf{u}_3$
2. $\mathbf{R}_1 = \mathbf{U} \mathbf{W} \mathbf{V}^T, \quad \mathbf{t}_2 = -\mathbf{u}_3$
3. $\mathbf{R}_2 = \mathbf{U} \mathbf{W}^T \mathbf{V}^T, \quad \mathbf{t}_1 = +\mathbf{u}_3$
4. $\mathbf{R}_2 = \mathbf{U} \mathbf{W}^T \mathbf{V}^T, \quad \mathbf{t}_2 = -\mathbf{u}_3$

The unique valid physical configuration is determined by chirality verification (the cheirality check): triangulating a 3D point and ensuring it has positive depth ($Z > 0$) in both camera coordinate systems.

### 4.4 3D Point Cloud Triangulation & Bundle Adjustment
Given camera matrices $P_1 = K [I | \mathbf{0}]$ and $P_2 = K [R | \mathbf{t}]$, each 3D point $\mathbf{X} = [X, Y, Z, 1]^T$ satisfies:

$$\mathbf{x}_1 \times (P_1 \mathbf{X}) = \mathbf{0}, \quad \mathbf{x}_2 \times (P_2 \mathbf{X}) = \mathbf{0}$$

**Bundle Adjustment** refines all 3D world coordinates $\mathbf{X}_i$ and camera parameters $\{R_j, \mathbf{t}_j\}$ simultaneously:

$$\min_{\{R_j, \mathbf{t}_j\}, \{\mathbf{X}_i\}} \sum_{i=1}^{M} \sum_{j=1}^{N} \rho\left( \left\| \mathbf{x}_{ij} - \pi(K, R_j, \mathbf{t}_j, \mathbf{X}_i) \right\|^2 \right)$$

Using Levenberg-Marquardt with Huber loss $\rho$ for outlier suppression.

### 4.5 Ground Plane Detection & 3D Text Pinning with Parallax
Once sparse 3D point clouds $\mathbf{X}_i$ are reconstructed:
1. **Plane Fitting**: RANSAC fits a plane model $\mathbf{n} \cdot \mathbf{X} + d = 0$.
2. **Coordinate Orientation**: Defines the ground plane normal $\mathbf{n}$ as the world $Y$-axis (Up), with origin on the detected floor.
3. **Parallax Projection**: A 3D typography element placed at world coordinates $\mathbf{X}_{\text{text}}$ projects to screen coordinates at frame $t$:
   $$\mathbf{p}_{\text{screen}}(t) \sim K \left[ R(t) \mid \mathbf{t}(t) \right] \begin{bmatrix} X_{\text{text}} \\ Y_{\text{text}} \\ Z_{\text{text}} \\ 1 \end{bmatrix}$$
When the mobile camera pans or dollies, foreground elements translate faster than background elements, producing realistic optical parallax.

---

## 5. AI Video Rotoscoping & Human Matting

```
Input Video Frames (I_t) ───► Recurrent ConvGRU ───► High-Resolution ───► Alpha Mask (α_t)
                               Temporal States         Refinement Module   (Trimap-Free)
                                     │                                         │
                                     ▼                                         ▼
                             Temporal Guidance                        Contour Extraction
                             (Eliminates Flicker)                              │
                                                                               ▼
                                                                      Neon Saber Shader
                                                                      (Bloom / Glow Blur)
```

### 5.1 The Alpha Matting Equation & Trimap Problem
In video compositing, an image $I$ is a linear combination of a foreground layer $F$ and a background layer $B$ governed by an opacity channel $\alpha \in [0, 1]$:

$$I(x, y) = \alpha(x, y) F(x, y) + (1 - \alpha(x, y)) B(x, y)$$

Given only $I$, determining $(F, B, \alpha)$ has 7 unknowns per pixel (3 color channels for $F$, 3 for $B$, 1 for $\alpha$) with only 3 known values ($I_R, I_G, I_B$), representing an underconstrained inverse problem.

Traditional matting requires a user-generated **trimap** classifying pixels into:
- Definitive Foreground ($\alpha = 1$)
- Definitive Background ($\alpha = 0$)
- Unknown Transition Region ($\alpha \in (0, 1)$)

Generating trimaps for thousands of video frames is labor-intensive. Modern mobile applications demand **trimap-free automatic video matting**.

### 5.2 RobustVideoMatting (RVM - ByteDance/CapCut WACV 2022)
Developed by ByteDance (arXiv:2108.11515) specifically for CapCut:
- **Core Problem with Frame-by-Frame Matting**: Processing frames independently produces temporal instability, high-frequency boundary flicker, and failure under motion blur.
- **RVM Architecture**:
  1. **Feature Extraction Backbone**: MobileNetV3 (lightweight variant) or ResNet50.
  2. **Recurrent Temporal Memory**: ConvGRU modules placed at $1/8$ and $1/16$ resolution stages maintain hidden state tensors $h_{t-1}$ across frames:
     $$h_t = \text{ConvGRU}(x_t, h_{t-1})$$
     Enables the network to remember occluded body parts and distinguish true human motion from moving background objects.
  3. **Deep Guided Filter High-Resolution Refinement**: Low-resolution coarse alpha masks are upsampled using high-resolution image features via bilateral guided filtering, preserving hair strands and fine garment contours.
- **Performance**: Achieves **4K at 76 FPS** and **HD at 104 FPS** on desktop GPUs, and **30+ FPS at 480p/720p** on mobile NPU/GPUs via ONNX Runtime / NCNN.

### 5.3 SAM 2: Segment Anything in Images and Videos (Meta AI 2024)
Meta AI's Segment Anything Model 2 (arXiv:2408.00714):
- **Streaming Memory Architecture**: Maintains a memory bank of past frame features and user interaction tokens.
- **Promptable Video Segmentation**: The user provides a single point tap or bounding box on frame 0. SAM 2 propagates the mask bidirectionally across the entire video sequence.
- **Memory Attention**: Frame features attend to the memory bank using multi-head cross-attention, allowing tracking through heavy occlusions and re-identification when subjects leave and re-enter the camera frame.

### 5.4 Google MediaPipe On-Device Selfie Segmentation
For ultra-lightweight mobile execution:
- Employs a modified U-Net architecture with MobileNetV3-like inverted bottleneck blocks.
- Takes $256 \times 256$ RGB input and outputs a continuous float alpha mask $\alpha \in [0.0, 1.0]$.
- Runs on Android hardware via TFLite GPU delegate in **under 8 milliseconds per frame** on modern Snapdragon / Dimensity chips.

### 5.5 Neon Saber / Edge Bloom Shader Synthesis
The iconic CapCut **Neon Saber / Glow Outline** effect is synthesized via multi-stage image processing on the alpha mask $\alpha(x, y)$:

```
  Alpha Mask α(x, y) ───► Morphological Gradient ───► Multi-Pass Gaussian Blur ───► Additive Composite
  (Cutout Subject)        (Sobel / Dilate - Erode)     (Bloom Pyramids)              (Neon Cyan/Pink)
```

1. **Edge Detection / Boundary Isolation**:
   $$E(x, y) = \text{clamp}\left( \|\nabla \alpha(x, y)\|, 0, 1 \right)$$
   Alternatively, via morphological dilation and erosion with kernel radius $r$:
   $$E_{\text{boundary}} = (\alpha \oplus K_r) - (\alpha \ominus K_r)$$
2. **Multi-Scale Gaussian Bloom (Dual-Filter Bloom)**:
   The boundary image is downsampled and blurred through 3-4 mipmap levels to generate smooth light falloff:
   $$B_k = G_{\sigma_k} \ast E_{\text{boundary}}, \quad \sigma \in \{2, 6, 14, 30\}$$
3. **Chroma Colorization & Tone Mapping**:
   $$C_{\text{glow}}(x, y) = \mathbf{C}_{\text{neon}} \cdot \sum_k w_k B_k(x, y)$$
4. **Final Scene Composition**:
   $$I_{\text{out}} = (1 - \alpha) \cdot I_{\text{background}} + C_{\text{glow}} + \alpha \cdot I_{\text{foreground}}$$

---

## 6. Mobile Implementation Blueprint for AIDITOR Android App

```
                                USER INTERACTION
                    (Tap to Track / Brush Mask / Preset Select)
                                       │
                                       ▼
                     AIDITOR TRACKING & ROTO COORDINATOR
                                       │
            ┌──────────────────────────┴──────────────────────────┐
            │                                                     │
            ▼                                                     ▼
   Tracking Engine (2D/3D)                               Roto & Matting Engine
  • MediaPipe / CSRT Tracking                           • On-Device Selfie Segmenter
  • Kalman Filter Smoothing                             • Alpha Matte Caching
  • Homography Matrix Compute                           • Neon Edge Outline Generator
            │                                                     │
            └──────────────────────────┬──────────────────────────┘
                                       │
                                       ▼
                     OPENGL ES 3.0 HARDWARE COMPOSITOR
                    • Real-Time TextureView Canvas Render
                    • Target-Lock Stabilizer Viewport Shift
                    • Neon Glow Multi-Pass Bloom Shader
                    • Interactive Scrubber at 60 FPS
```

### 6.1 Real-Time UI Preview vs Offline Render Engine
- **Preview Tier (Interactive Canvas)**:
  - Downscales frames to $360 \times 640$ for immediate on-device tracking and matting.
  - Updates tracking vectors and keyframe curves live in Kotlin `StateFlow`.
  - Employs an OpenGL ES fragment shader for instantaneous edge glow previews on `TextureView`.
- **Export Tier (Full Resolution 1080p/4K)**:
  - Executes batch tracking and frame matting inside a foreground background worker.
  - Generates lossless intermediate alpha channels (ProRes 4444 or PNG sequence).
  - Encodes through hardware MediaCodec H.264 / HEVC at high bitrate.

### 6.2 Jetpack Compose Inspector Sheet & Touch Interaction
Controls to incorporate into `ToolInspectorSheet.kt`:
1. **Subject Tracking Mode**:
   - Tap-to-select tracking target with visual reticle HUD.
   - Mode Selector: `HUD Callout` | `Face Lock` | `Body Anchor` | `Point Track`.
   - Smoothing Slider: Kalman process noise damping ($0.1 - 1.0$).
2. **Camera Tracking / Planar Mode**:
   - 4-Point Corner Pin overlay with draggable magnification loupes.
   - Ground Plane Grid visualizer with 3D orientation arrows.
   - Surface Text input with 3D perspective depth slider.
3. **Rotoscoping & Neon Saber Mode**:
   - Master Switch: Cutout Enable / Disable.
   - Style Selector: `Neon Saber` | `Cyberpunk Glow` | `Silhouette` | `Ghost Trailing`.
   - Outline Thickness ($1\text{px} - 40\text{px}$) and Glow Intensity ($0.0 - 2.0$).
   - Neon Color Palette (Electric Cyan `0x00F0FF`, Acid Green `0x39FF14`, Hot Pink `0xFF007F`, Solar Yellow `0xFFE600`).

### 6.3 OpenGL ES 3.0 / MediaCodec GPU Shader Pipeline
Fragment Shader for Real-Time Neon Saber Rendering:
```glsl
#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uVideoTexture;   // RGB Video Stream
uniform sampler2D uAlphaMask;       // Grayscale Matting Mask (R8)
uniform vec3 uGlowColor;           // e.g. vec3(0.0, 0.94, 1.0)
uniform float uOutlineWidth;       // Pixel radius
uniform float uGlowIntensity;      // Multiplier

void main() {
    vec4 video = texture(uVideoTexture, vTexCoord);
    float alpha = texture(uAlphaMask, vTexCoord).r;

    // Fast 8-tap boundary gradient
    vec2 texel = 1.0 / vec2(textureSize(uAlphaMask, 0));
    float d = 0.0;
    d += abs(alpha - texture(uAlphaMask, vTexCoord + vec2( texel.x, 0.0)).r);
    d += abs(alpha - texture(uAlphaMask, vTexCoord + vec2(-texel.x, 0.0)).r);
    d += abs(alpha - texture(uAlphaMask, vTexCoord + vec2(0.0,  texel.y)).r);
    d += abs(alpha - texture(uAlphaMask, vTexCoord + vec2(0.0, -texel.y)).r);
    float edge = clamp(d * uOutlineWidth, 0.0, 1.0);

    // Composite: Video + Saturated Neon Edge
    vec3 glow = uGlowColor * edge * uGlowIntensity;
    fragColor = vec4(video.rgb + glow, 1.0);
}
```

---

## 7. Academic & Technical References

1. **Bolme, D. S., Beveridge, J. R., Draper, B. A., & Lui, Y. M. (2010)**. *Visual object tracking using adaptive correlation filters*. Computer Vision and Pattern Recognition (CVPR 2010), 2544-2550.
2. **Henriques, J. F., Caseiro, R., Martins, P., & Batista, J. (2015)**. *High-speed tracking with kernelized correlation filters*. IEEE Transactions on Pattern Analysis and Machine Intelligence, 37(3), 583-596.
3. **Lukežič, A., Vojíř, T., Čehovin, L., Matas, J., & Kristan, M. (2017)**. *Discriminative correlation filter with channel and spatial reliability*. Computer Vision and Pattern Recognition (CVPR 2017), 6309-6318.
4. **Karaev, N., Rocco, I., Graham, B., Neverova, N., Vedaldi, A., & Rupprecht, C. (2023)**. *CoTracker: It is Better to Track Together*. European Conference on Computer Vision (ECCV 2024 / arXiv:2307.07635).
5. **Baker, S., & Matthews, I. (2004)**. *Lucas-Kanade 20 years on: A unifying framework*. International Journal of Computer Vision, 56(3), 221-255.
6. **Nistér, D. (2004)**. *An efficient solution to the five-point relative pose problem*. IEEE Transactions on Pattern Analysis and Machine Intelligence, 26(6), 756-770.
7. **Lin, S., Ryabtsev, A., Sengupta, S., Curless, B., Seitz, S., & Kemelmacher-Shlizerman, I. (2022)**. *Robust High-Resolution Video Matting with Temporal Guidance*. IEEE Winter Conference on Applications of Computer Vision (WACV 2022 / ByteDance Inc., arXiv:2108.11515).
8. **Ravi, N., Gabeur, V., Hu, Y. T., Hu, R., Ryali, C., Ma, T., ... & Feichtenhofer, C. (2024)**. *SAM 2: Segment Anything in Images and Videos*. Meta AI Research, arXiv:2408.00714.
9. **Google LLC (2024)**. *MediaPipe Image Segmenter & Object Tracking Solutions*. Google AI Edge Documentation: https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter.
