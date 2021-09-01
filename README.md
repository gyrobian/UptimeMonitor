# UptimeMonitor
Service which monitors the uptime of sites, and can be used to generate performance reports.

# Configuration
To set up the monitor, you need to define a `config.yaml` configuration file, either in the monitor's working directory, or in another location specified on the command line with the `--config` option.

Here is an example:
```yaml
# The target maximum size for record files. Supports MB only.
maxFileSize: 1MB
# The list of sites to monitor.
sites:
  - name: google # The name of the site.
    url: https://www.google.com # The URL to check.
    interval: 60 # The monitoring interval, in seconds.

```
