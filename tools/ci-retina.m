// A virtual Retina display for GitHub's macOS runners (.github/workflows/tours.yml). The runner's own display is 1x and
// at most 1920x1080, so a tour would shoot half the Mac's pixels at another GUI scale (and a script's GUI clicks would
// miss). This adds a display that looks like the owner's MacBook (1512x982 points at 2x), makes it the main display and
// keeps it until killed. CoreGraphics' CGVirtualDisplay is private API (macOS 11+; DeskPad and BetterDisplay use it).
// CI only: on a Mac it would add a display to the desktop.
// Build: clang -fobjc-arc -framework Foundation -framework CoreGraphics tools/ci-retina.m -o <out>
// Run:   <out> [<width> <height>] &    (points; prints "main <id> <w>x<h> pt = <pw>x<ph> px" once it is the main display)
#import <CoreGraphics/CoreGraphics.h>
#import <Foundation/Foundation.h>

@interface CGVirtualDisplayDescriptor : NSObject
@property(retain, nonatomic) NSString *name;
@property(nonatomic) unsigned int maxPixelsWide;
@property(nonatomic) unsigned int maxPixelsHigh;
@property(nonatomic) CGSize sizeInMillimeters;
@property(nonatomic) unsigned int serialNum;
@property(nonatomic) unsigned int productID;
@property(nonatomic) unsigned int vendorID;
- (void)setDispatchQueue:(dispatch_queue_t)queue;
@end

@interface CGVirtualDisplayMode : NSObject
- (instancetype)initWithWidth:(unsigned int)width height:(unsigned int)height refreshRate:(double)refreshRate;
@end

@interface CGVirtualDisplaySettings : NSObject
@property(retain, nonatomic) NSArray *modes;
@property(nonatomic) unsigned int hiDPI;
@end

@interface CGVirtualDisplay : NSObject
- (instancetype)initWithDescriptor:(CGVirtualDisplayDescriptor *)descriptor;
- (BOOL)applySettings:(CGVirtualDisplaySettings *)settings;
@property(readonly, nonatomic) unsigned int displayID;
@end

static CGDisplayModeRef retinaMode(CGDirectDisplayID display, size_t width, size_t height) {
    NSDictionary *options = @{(__bridge NSString *)kCGDisplayShowDuplicateLowResolutionModes: @YES};
    NSArray *modes = CFBridgingRelease(CGDisplayCopyAllDisplayModes(display, (__bridge CFDictionaryRef)options));
    for (id m in modes) {
        CGDisplayModeRef mode = (__bridge CGDisplayModeRef)m;
        if (CGDisplayModeGetWidth(mode) == width && CGDisplayModeGetHeight(mode) == height
                && CGDisplayModeGetPixelWidth(mode) == 2 * width) return CGDisplayModeRetain(mode);
    }
    return NULL;
}

int main(int argc, const char *argv[]) {
    @autoreleasepool {
        unsigned int w = argc > 2 ? (unsigned int) atoi(argv[1]) : 1512, h = argc > 2 ? (unsigned int) atoi(argv[2]) : 982;
        Class descriptorClass = NSClassFromString(@"CGVirtualDisplayDescriptor");
        if (descriptorClass == nil) {
            fprintf(stderr, "no CGVirtualDisplay on this macOS\n");
            return 1;
        }
        CGVirtualDisplayDescriptor *descriptor = [[descriptorClass alloc] init];
        [descriptor setDispatchQueue:dispatch_get_main_queue()];
        descriptor.name = @"AWAKEN tour display";
        descriptor.maxPixelsWide = 2 * w;
        descriptor.maxPixelsHigh = 2 * h;
        descriptor.sizeInMillimeters = CGSizeMake(302, 196); // a 14-inch MacBook Pro's panel: about 254 points an inch
        descriptor.serialNum = 1;
        descriptor.productID = 0xA3A1;
        descriptor.vendorID = 0xA3A1;
        CGVirtualDisplay *display = [[NSClassFromString(@"CGVirtualDisplay") alloc] initWithDescriptor:descriptor];
        if (display == nil || display.displayID == 0) {
            fprintf(stderr, "the virtual display was refused\n");
            return 1;
        }
        CGVirtualDisplaySettings *settings = [[NSClassFromString(@"CGVirtualDisplaySettings") alloc] init];
        settings.hiDPI = 1; // each mode also offered at half its size in points, two pixels a point
        settings.modes = @[[[NSClassFromString(@"CGVirtualDisplayMode") alloc] initWithWidth:2 * w height:2 * h refreshRate:60]];
        if (![display applySettings:settings]) {
            fprintf(stderr, "the display's modes were refused\n");
            return 1;
        }
        CGDirectDisplayID id = display.displayID, old = CGMainDisplayID();
        CGDisplayModeRef mode = NULL;
        for (int i = 0; i < 50 && mode == NULL; i++) { // the window server takes a moment to list the new display
            [NSThread sleepForTimeInterval:0.1];
            mode = retinaMode(id, w, h);
        }
        if (mode == NULL) {
            fprintf(stderr, "display %u has no %ux%u mode at 2x\n", id, w, h);
            return 1;
        }
        // the main display is the one at the origin: the virtual one there, the runner's own beside it
        CGDisplayConfigRef config;
        CGBeginDisplayConfiguration(&config);
        CGConfigureDisplayWithDisplayMode(config, id, mode, NULL);
        CGConfigureDisplayOrigin(config, id, 0, 0);
        if (old != id) CGConfigureDisplayOrigin(config, old, (int32_t) w, 0);
        CGError err = CGCompleteDisplayConfiguration(config, kCGConfigureForSession);
        CGDisplayModeRelease(mode);
        if (err != kCGErrorSuccess || CGMainDisplayID() != id) {
            fprintf(stderr, "could not make display %u the main one (error %d)\n", id, err);
            return 1;
        }
        CGDisplayModeRef now = CGDisplayCopyDisplayMode(id);
        printf("main %u %zux%zu pt = %zux%zu px\n", id, CGDisplayModeGetWidth(now), CGDisplayModeGetHeight(now),
               CGDisplayModeGetPixelWidth(now), CGDisplayModeGetPixelHeight(now));
        fflush(stdout);
        CGDisplayModeRelease(now);
        dispatch_main(); // the display lives as long as this process
    }
}
