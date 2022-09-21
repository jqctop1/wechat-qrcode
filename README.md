# wechat-qrcode
An android library which use OpenCV Wechat-QRCode to decode qrcode

### NOTICE!!! Before Your Compile
you must use 7z to unzip wechat-scan\libs\wechat-scan-libs.zip to wechat-scan\libs.  

wechat-scan-libs.zip contains static libraries which are compiled from [opecv](https://github.com/opencv/opencv) and [opecv_contrib](https://github.com/opencv/opencv_contrib)

### How to Use
We support decode qrcode from the camera preview and image files.   

The **ScanQRCodeView** which extends **CameraPreviewView** support to decode qrcode from the camera preview. The **CameraPreviewView** support preview、take pictures and other camera operations.   

You can extends **ScanQRCodeView** or even **CameraPreviewView** to support the features you want.   

The **ScanDecodeQueue** implement the core process of decode qrcode from the camera preview (It only support YUV_420_888 preview format).   
The **FileDecodeQueue** implement the process of decode image files or RGB Bitmap.

That is all !
