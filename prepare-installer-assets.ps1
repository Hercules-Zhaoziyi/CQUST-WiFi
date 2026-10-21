$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$assetDir=Join-Path $PSScriptRoot 'src-tauri/icons'
$seal=[Drawing.Image]::FromFile((Join-Path $assetDir 'school-seal.png'))
try{
 $header=New-Object Drawing.Bitmap 150,57
 $canvas=[Drawing.Graphics]::FromImage($header)
 $canvas.Clear([Drawing.Color]::White)
 $canvas.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
 $canvas.DrawImage($seal,98,4,49,49)
 $header.Save((Join-Path $assetDir 'installer-header.bmp'),[Drawing.Imaging.ImageFormat]::Bmp)
 $canvas.Dispose();$header.Dispose()
 $bitmap=New-Object Drawing.Bitmap 64,64
 $canvas=[Drawing.Graphics]::FromImage($bitmap)
 $canvas.Clear([Drawing.Color]::White)
 $canvas.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
 $canvas.DrawImage($seal,0,0,64,64)
 $icon=[Drawing.Icon]::FromHandle($bitmap.GetHicon())
 $stream=[IO.File]::Create((Join-Path $assetDir 'installer-school.ico'))
 try{$icon.Save($stream)}finally{$stream.Dispose();$icon.Dispose();$canvas.Dispose();$bitmap.Dispose()}
}finally{$seal.Dispose()}
