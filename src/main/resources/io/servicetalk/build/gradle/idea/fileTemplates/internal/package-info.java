#parse("License.java")
#parse("File Header.java")
@ElementsAreNonnullByDefault
#if (${PACKAGE_NAME} && ${PACKAGE_NAME} != "")package ${PACKAGE_NAME};#end

import io.servicetalk.annotations.ElementsAreNonnullByDefault;
