/* Native OS vault boundary. Secrets travel only through bounded stdin/stdout.
 * No shell, environment credential, file fallback, or native error text. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum { OK = 0, MISSING = 10, LOCKED = 11, UNAVAILABLE = 12, CANCELLED = 13, INVALID = 14, FAILED = 15, DENIED = 16 };
#define LIMIT 4096
static void wipe(void *p, size_t n) { volatile unsigned char *v = p; while (n--) *v++ = 0; }
static int valid_secret(const unsigned char *p, size_t n) {
    if (!n || n > LIMIT) return 0;
    for (size_t i = 0; i < n; i++) if (p[i] < 33 || p[i] > 126) return 0;
    return 1;
}
static int input(unsigned char *data, size_t *length) {
    *length = fread(data, 1, LIMIT + 1, stdin);
    if (ferror(stdin) || !valid_secret(data, *length)) return INVALID;
    data[*length] = 0; return OK;
}
static int output(const unsigned char *data, size_t length) {
    if (!valid_secret(data, length)) return INVALID;
    return fwrite(data, 1, length, stdout) == length && fflush(stdout) == 0 ? OK : FAILED;
}

#ifdef __APPLE__
#include <CoreFoundation/CoreFoundation.h>
#include <Security/Security.h>
static int result(OSStatus code) {
    if (code == errSecSuccess) return OK;
    if (code == errSecItemNotFound) return MISSING;
    if (code == errSecInteractionNotAllowed) return LOCKED;
    if (code == errSecAuthFailed || code == errSecNoAccessForItem) return DENIED;
    if (code == errSecUserCanceled) return CANCELLED;
    if (code == errSecNoDefaultKeychain || code == errSecInvalidKeychain || code == errSecNotAvailable) return UNAVAILABLE;
    return FAILED;
}
static CFStringRef string(const char *value) {
    return CFStringCreateWithBytes(NULL, (const UInt8 *)value, (CFIndex)strlen(value), kCFStringEncodingUTF8, false);
}
static CFMutableDictionaryRef query(SecKeychainRef keychain, const char *kind, const char *id, int adding) {
    CFMutableDictionaryRef q = CFDictionaryCreateMutable(NULL, 0, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks);
    char account[80]; snprintf(account, sizeof(account), "%s.%s", kind, id);
    CFStringRef service = CFSTR("ai.wckd.niobium.desktop.credentials.v1"), a = string(account);
    CFDictionarySetValue(q, kSecClass, kSecClassGenericPassword);
    CFDictionarySetValue(q, kSecAttrService, service); CFDictionarySetValue(q, kSecAttrAccount, a); CFRelease(a);
    CFDictionarySetValue(q, kSecUseDataProtectionKeychain, kCFBooleanFalse);
    if (adding) CFDictionarySetValue(q, kSecUseKeychain, keychain);
    else {
        CFArrayRef keys = CFArrayCreate(NULL, (const void **)&keychain, 1, &kCFTypeArrayCallBacks);
        CFDictionarySetValue(q, kSecMatchSearchList, keys); CFRelease(keys);
    }
    return q;
}
static int operate(const char *op, const char *kind, const char *id, const char *label,
                   const unsigned char *data, size_t length, SecKeychainRef keychain) {
    SecKeychainStatus status = 0;
    int r = result(SecKeychainGetStatus(keychain, &status));
    if (r != OK) return r;
    if (!(status & kSecUnlockStateStatus)) return LOCKED;
    if (!strcmp(op, "status")) return OK;
    CFMutableDictionaryRef q = query(keychain, kind, id, 0);
    if (!strcmp(op, "get")) {
        CFDictionarySetValue(q, kSecReturnData, kCFBooleanTrue);
        CFDictionarySetValue(q, kSecMatchLimit, kSecMatchLimitOne);
        CFTypeRef value = NULL; r = result(SecItemCopyMatching(q, &value));
        if (r == OK) {
            r = value && CFGetTypeID(value) == CFDataGetTypeID() ? output(CFDataGetBytePtr(value), (size_t)CFDataGetLength(value)) : INVALID;
            if (value) CFRelease(value);
        }
    } else if (!strcmp(op, "delete")) {
        r = result(SecItemDelete(q)); if (r == MISSING) r = OK;
    } else {
        CFDataRef value = CFDataCreate(NULL, data, (CFIndex)length);
        CFStringRef name = string(label);
        CFMutableDictionaryRef updates = CFDictionaryCreateMutable(NULL, 0, &kCFTypeDictionaryKeyCallBacks, &kCFTypeDictionaryValueCallBacks);
        CFDictionarySetValue(updates, kSecValueData, value); CFDictionarySetValue(updates, kSecAttrLabel, name);
        r = result(SecItemUpdate(q, updates));
        if (r == MISSING) {
            CFMutableDictionaryRef add = query(keychain, kind, id, 1);
            CFDictionarySetValue(add, kSecValueData, value); CFDictionarySetValue(add, kSecAttrLabel, name);
            r = result(SecItemAdd(add, NULL)); CFRelease(add);
        }
        CFRelease(value); CFRelease(name); CFRelease(updates);
    }
    CFRelease(q); return r;
}
#else
#include <libsecret/secret.h>
static const SecretSchema schema = {
    .name = "ai.wckd.niobium.Credential.v1", .flags = SECRET_SCHEMA_NONE,
    .attributes = {{"kind", SECRET_SCHEMA_ATTRIBUTE_STRING}, {"account", SECRET_SCHEMA_ATTRIBUTE_STRING}, {NULL, 0}}
};
static int error_result(GError *error) {
    int r = FAILED;
    if (error) {
        if (g_error_matches(error, SECRET_ERROR, SECRET_ERROR_IS_LOCKED)) r = LOCKED;
        if (g_error_matches(error, G_IO_ERROR, G_IO_ERROR_CANCELLED)) r = CANCELLED;
        if (g_error_matches(error, G_IO_ERROR, G_IO_ERROR_PERMISSION_DENIED)) r = DENIED;
        g_error_free(error);
    }
    return r;
}
static int operate(const char *op, const char *kind, const char *id, const char *label,
                   const unsigned char *data, size_t length) {
    GError *error = NULL;
    SecretService *service = secret_service_get_sync(SECRET_SERVICE_NONE, NULL, &error);
    if (!service) { if (error) g_error_free(error); return UNAVAILABLE; }
    SecretCollection *collection = secret_collection_for_alias_sync(service, SECRET_COLLECTION_DEFAULT, SECRET_COLLECTION_NONE, NULL, &error);
    if (!collection) { if (error) g_error_free(error); g_object_unref(service); return UNAVAILABLE; }
    int r = secret_collection_get_locked(collection) ? LOCKED : OK;
    if (r == OK && strcmp(op, "status")) {
        GHashTable *attributes = secret_attributes_build(&schema, "kind", kind, "account", id, NULL);
        if (!strcmp(op, "put")) {
            SecretValue *value = secret_value_new((const char *)data, (gssize)length, "text/plain; charset=utf8");
            if (!secret_service_store_sync(service, &schema, attributes, g_dbus_proxy_get_object_path(G_DBUS_PROXY(collection)), label, value, NULL, &error)) r = error_result(error);
            secret_value_unref(value);
        } else {
            GList *items = secret_collection_search_sync(collection, &schema, attributes, SECRET_SEARCH_NONE, NULL, &error);
            if (error) r = error_result(error);
            else if (!items) r = !strcmp(op, "delete") ? OK : MISSING;
            else if (items->next) r = INVALID;
            else {
                SecretItem *item = items->data;
                if (secret_item_get_locked(item)) r = LOCKED;
                else if (!strcmp(op, "delete")) { if (!secret_item_delete_sync(item, NULL, &error)) r = error_result(error); }
                else if (!secret_item_load_secret_sync(item, NULL, &error)) r = error_result(error);
                else {
                    SecretValue *value = secret_item_get_secret(item);
                    if (!value) r = MISSING;
                    else { gsize count = 0; const char *bytes = secret_value_get(value, &count); r = output((const unsigned char *)bytes, count); secret_value_unref(value); }
                }
            }
            g_list_free_full(items, g_object_unref);
        }
        g_hash_table_unref(attributes);
    }
    g_object_unref(collection); g_object_unref(service); return r;
}
#endif

int main(int argc, char **argv) {
    const char *op = argc > 1 ? argv[1] : "";
    int status = !strcmp(op, "status"), put = !strcmp(op, "put");
    if ((!status && strcmp(op, "get") && strcmp(op, "delete") && !put) || argc != (status ? 2 : put ? 5 : 4)) return INVALID;
    const char *kind = status ? "" : argv[2], *id = status ? "" : argv[3], *label = put ? argv[4] : "";
    if (!status) {
        if (strcmp(kind, "gateway") && strcmp(kind, "images")) return INVALID;
        if (strlen(id) != 64) return INVALID;
        for (size_t i = 0; i < 64; i++) if (!strchr("0123456789abcdef", id[i])) return INVALID;
        if (strlen(label) > 512) return INVALID;
    }
    unsigned char data[LIMIT + 2] = {0}; size_t length = 0;
    int r = put ? input(data, &length) : OK;
    if (r == OK) {
#ifdef __APPLE__
        if (status) SecKeychainSetUserInteractionAllowed(false);
        SecKeychainRef keychain = NULL;
        r = result(SecKeychainCopyDefault(&keychain));
        if (r == OK) { r = operate(op, kind, id, label, data, length, keychain); CFRelease(keychain); }
#else
        r = operate(op, kind, id, label, data, length);
#endif
    }
    wipe(data, sizeof(data)); return r;
}
