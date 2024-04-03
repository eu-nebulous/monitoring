
# -----------------   Runtime image   -----------------
FROM registry.gitlab.com/nebulous-project/ems-main/ems-server:opendev3
RUN date > /tmp/BUILD-TIMESTAMP
ENV EXTRA_LOADER_PATHS=/plugins/*
ENV SCAN_PACKAGES=eu.nebulous.ems
