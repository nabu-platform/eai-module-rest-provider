# Artifact: REST interface

Fragments:
- `metadata.xml`: repository metadata around the artifact
- `rest-api.xml`: REST interface configuration

## Fragment: rest-api.xml

Use `rest-api.xml` to read and update the REST interface definition.

Structure definitions:
- `query`: Query parameter structure.
- `cookie`: Cookie parameter structure.
- `session`: Session parameter structure.
- `header`: Request header structure.
- `responseHeader`: Response header structure.
- `pathParameters`: Path parameter structure.
Before editing any structure definition, load skill `artifact:structure` if you haven't yet.
The variable names must follow naming conventions, use `alias` property if the actual name does not.
Don't change the alias unless you have good reason to.

Fields:
- `method`: HTTP method.
- `path`: URL path pattern, e.g. `/my/path/{variable}`. Variable defined in path can be tweaked in `pathParameters`
- `role`: Required role for user calling it
- `permissionAction`: Permission action name.
- `permissionContext`: Permission-check context expression. Can use leading `=` to extract from input, e.g. `=input/path/id`
- `permissionContextType`: Optional permission-check context source. One of `SERVICE_CONTEXT`, `WEB_APPLICATION`, `PROJECT`, `GLOBAL`
- `preferredResponseType`: Default response format.
- `inputAsStream`: Read request body as stream.
- `outputAsStream`: Write response body as stream.
- `input`: Request body type. Must reference existing structure. Available on pipeline as `input/content`
- `output`: Response body type. Must reference existing structure. Available on pipeline as `output/content`
- `device`: Expose device input in the input of the service.
- `deviceBestEffort`: Resolve device best effort. By default if device is requested, it will always be created.
- `token`: Expose token in service input.
- `lenient`: Parse input leniently.
- `useAsAuthorizationServiceContext`: Use as authorization service context.
- `allowFormBinding`: Allow form binding.
- `cache`: Enable cache output support.
- `caseInsensitive`: Match path case-insensitively.
- `allowCookiesWithoutReferer`: Accept cookies without referer.
- `allowCookiesWithExternalReferer`: Accept cookies with external referer.
- `allowHeaderAsQueryParameter`: Allow headers through query parameters.
- `useServerCache`: Use server-side cache handling.
- `allowRaw`: Allow raw payload handling.
- `temporaryAlias`: Temporary authentication alias.
- `temporarySecret`: Temporary authentication secret.
- `temporaryCorrelationId`: Temporary authentication correlation id.
- `rateLimitContext`: Rate limit context key.
- `rateLimitAction`: Rate limit action key.
- `ignoreOffline`: Ignore offline mode.
- `allowRootArrays`: Allow JSON root arrays.
- `parent`: Parent REST interface.
- `stubbed`: Return stubbed data.
- `clusterLock`: Serialize execution across the cluster. Keyed on path parameters.
- `captureErrors`: Capture error responses.
- `captureSuccessful`: Capture successful responses.

Configure to enrich input of service:
- `webApplicationId`: Expose web application id.
- `geoPosition`: Expose geo position.
- `language`: Expose resolved language.
- `acceptedLanguages`: Expose accepted languages input.
- `configurationType`: A defined type injected as configuration.
- `request`: Expose raw HTTP request.
- `source`: Expose request source.
- `domain`: Expose request domain.
- `origin`: Expose request origin.
- `scheme`: Expose request scheme.

Configure to enrich output of service:
- `allowExplicitResponseCode`: Allow explicit response code output.

Schema for rest-interface XML:
```ts
type PermissionContextType = "SERVICE_CONTEXT" | "WEB_APPLICATION" | "PROJECT" | "GLOBAL";
type RestApiXml = { restInterface: { method?: string; path?: string; query?: StructureXml; cookie?: StructureXml; session?: StructureXml; header?: StructureXml; pathParameters?: StructureXml; responseHeader?: StructureXml; role?: string | string[]; permissionAction?: string; permissionContext?: string; permissionContextType?: PermissionContextType; preferredResponseType?: string; inputAsStream?: boolean; outputAsStream?: boolean; input?: string; output?: string; acceptedLanguages?: boolean; configurationType?: string; device?: boolean; deviceBestEffort?: boolean; token?: boolean; lenient?: boolean; webApplicationId?: boolean; geoPosition?: boolean; useAsAuthorizationServiceContext?: boolean; language?: boolean; allowFormBinding?: boolean; caseInsensitive?: boolean; cache?: boolean; allowCookiesWithoutReferer?: boolean; allowCookiesWithExternalReferer?: boolean; request?: boolean; allowHeaderAsQueryParameter?: boolean; useServerCache?: boolean; source?: boolean; allowRaw?: boolean; domain?: boolean; origin?: boolean; scheme?: boolean; temporaryAlias?: string; temporarySecret?: string; temporaryCorrelationId?: string; rateLimitContext?: string; rateLimitAction?: string; ignoreOffline?: boolean; allowRootArrays?: boolean; captureErrors?: boolean; captureSuccessful?: boolean; parent?: string; allowExplicitResponseCode?: boolean; stubbed?: boolean; clusterLock?: boolean; }; };
```
