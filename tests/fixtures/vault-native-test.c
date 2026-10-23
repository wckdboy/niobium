/* Test-only controls are never compiled into the packaged helper. */
#define main niobium_production_vault_main
#include "../../desktop/native/vault.c"
#undef main

int main(int argc, char **argv) {
    if (argc < 3) return INVALID;
#ifdef __APPLE__
    const char *op = argv[1], *path = argv[2];
    if (!strstr(path, "niobium-vault-test-") || !strstr(path, "/isolated.keychain")) return INVALID;
    SecKeychainSetUserInteractionAllowed(false);
    SecKeychainRef keychain = NULL;
    unsigned char data[LIMIT + 2] = {0}; size_t length = 0;
    int r = OK;
    if (!strcmp(op, "create") || !strcmp(op, "unlock") || !strcmp(op, "put")) r = input(data, &length);
    if (r != OK) return r;
    if (!strcmp(op, "create")) r = result(SecKeychainCreate(path, (UInt32)length, data, false, NULL, &keychain));
    else {
        r = result(SecKeychainOpen(path, &keychain));
        if (r == OK) {
            if (!strcmp(op, "lock")) r = result(SecKeychainLock(keychain));
            else if (!strcmp(op, "unlock")) r = result(SecKeychainUnlock(keychain, (UInt32)length, data, true));
            else if (!strcmp(op, "destroy")) r = result(SecKeychainDelete(keychain));
            else if (!strcmp(op, "status")) r = operate(op, "", "", "", NULL, 0, keychain);
            else if (argc >= 5) r = operate(op, argv[3], argv[4], argc > 5 ? argv[5] : "", data, length, keychain);
            else r = INVALID;
        }
    }
    if (keychain) CFRelease(keychain);
    wipe(data, sizeof(data)); return r;
#else
    if (strcmp(argv[1], "lock")) return INVALID;
    GError *error = NULL;
    SecretService *service = secret_service_get_sync(SECRET_SERVICE_NONE, NULL, &error);
    if (!service) return UNAVAILABLE;
    SecretCollection *collection = secret_collection_for_alias_sync(service, SECRET_COLLECTION_DEFAULT, SECRET_COLLECTION_NONE, NULL, &error);
    if (!collection) { g_object_unref(service); return UNAVAILABLE; }
    GList *objects = g_list_append(NULL, collection), *locked = NULL;
    int r = secret_service_lock_sync(service, objects, NULL, &locked, &error) < 0 ? error_result(error) : OK;
    g_list_free(objects); g_list_free_full(locked, g_object_unref); g_object_unref(collection); g_object_unref(service);
    return r;
#endif
}
