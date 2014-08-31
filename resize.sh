mkdir res/drawable-xhdpi
mkdir res/drawable-hdpi
mkdir res/drawable-mdpi
mkdir res/drawable-ldpi

convert icon.png -resize 512x512 icon512.png
convert icon.png -resize 96x96 res/drawable-xhdpi/icon.png
convert icon.png -resize 72x72 res/drawable-hdpi/icon.png
convert icon.png -resize 48x48 res/drawable-mdpi/icon.png
convert icon.png -resize 36x36 res/drawable-ldpi/icon.png

#convert pins.png -resize 500x500 res/drawable-xhdpi/pins.png
#convert pins.png -resize 375x375 res/drawable-hdpi/pins.png
#convert pins.png -resize 250x250 res/drawable-mdpi/pins.png
#convert pins.png -resize 180x180 res/drawable-ldpi/pins.png