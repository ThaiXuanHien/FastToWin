FROM redis@sha256:be7c1cada5a6eb1656adfa2635b99296af725881301b21f84f35a71fac072c7f
COPY boot.sh /usr/local/bin/ftw-redis-boot.sh
COPY backup.sh /usr/local/bin/ftw-redis-backup.sh
RUN sed -i 's/\r$//' /usr/local/bin/ftw-redis-boot.sh /usr/local/bin/ftw-redis-backup.sh
ENTRYPOINT ["/bin/sh", "/usr/local/bin/ftw-redis-boot.sh"]