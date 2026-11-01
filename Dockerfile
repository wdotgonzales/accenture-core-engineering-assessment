FROM groovy:jdk17

# Copy the script into the image
COPY TextScan.groovy /app/TextScan.groovy

# Pre-download Picocli at build time so runs need no internet
RUN groovy -e "@Grab('info.picocli:picocli:4.7.6') import picocli.CommandLine; println 'deps cached'"

# Scans happen against whatever folder is mounted here
WORKDIR /work

ENTRYPOINT ["groovy", "/app/TextScan.groovy"]
