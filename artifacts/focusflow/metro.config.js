const { getDefaultConfig } = require("expo/metro-config");
const path = require("path");

process.env.EXPO_ROUTER_APP_ROOT = "app";

const projectRoot = __dirname;
const workspaceRoot = path.resolve(projectRoot, "../..");

const config = getDefaultConfig(projectRoot);

// Watch all files within the monorepo root so Metro can find shared packages
config.watchFolders = [workspaceRoot];

// Tell Metro where to find packages — app-level first, then workspace root
config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, "node_modules"),
  path.resolve(workspaceRoot, "node_modules"),
];

// Prevent Metro from climbing up the directory tree beyond nodeModulesPaths
config.resolver.disableHierarchicalLookup = true;

// Intercept expo-router's internal _ctx import and redirect to local overrides
// that use hardcoded string literals instead of process.env.EXPO_ROUTER_APP_ROOT.
// Metro validates require.context() args via its own AST parser BEFORE Babel runs,
// so env vars can never be substituted in time — this override is the only real fix.
const originalResolveRequest = config.resolver.resolveRequest;

config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (
    moduleName === "./_ctx" &&
    context.originModulePath.includes("/expo-router/")
  ) {
    const overrideFile = path.resolve(
      __dirname,
      `_ctx-override.${platform}.js`
    );
    return { filePath: overrideFile, type: "sourceFile" };
  }

  if (originalResolveRequest) {
    return originalResolveRequest(context, moduleName, platform);
  }
  return context.resolveRequest(context, moduleName, platform);
};

module.exports = config;
