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

# Reporting
Aggregate data reports can be generated via the `measure` subcommand.
```
java -jar uptime-monitor.jar measure <site>
```
The following options are accepted:
- `--start` - An optional starting date for the measurement, in ISO8601 format (`yyyy-MM-dd`). Only record entries logged after the given date (at the start of that day) are counted.
- `--end` - An optional ending date for the measurement, in ISO8601 format (`yyyy-MM-dd`). Only record entries logged before the given date (at the end of that day) are counted.
- `--format` - The format in which to produce output. Supported options are `TEXT`, `JSON`, or `PDF`.
- `-o` or `--output` - A path at which to send the generated report. This should be a path to a file. *Caution*, this will overwrite any file at the specified path. If no output path is given, data is output to standard output.
