//
//  DSHAppDelegate.m
//  Niobium
//
//  DSH/iSH app delegate. Unlike the upstream DSH app, Niobium does not boot the
//  Alpine guest at launch: the guest is started only when the user opens
//  Settings → On device → DeepSeek Harness.
//

#import "DSHAppDelegate.h"
#import "DSHHarness.h"

@implementation DSHAppDelegate

- (BOOL)application:(UIApplication *)application
    willFinishLaunchingWithOptions:(NSDictionary<UIApplicationLaunchOptionsKey, id> *)launchOptions {
    return YES;
}

- (BOOL)application:(UIApplication *)application
    didFinishLaunchingWithOptions:(NSDictionary<UIApplicationLaunchOptionsKey, id> *)launchOptions {
    return YES;
}

- (void)applicationWillEnterForeground:(UIApplication *)application {
    if (DSHHarness.shared.state == DSHHarnessStateReady) {
        [DSHHarness.shared verifyAliveWithCompletion:nil];
    }
}

@end
