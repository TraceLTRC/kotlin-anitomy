*Last Updated: 05/13/2024 (MM/DD/YYYY)*

This folder contains two datasets that helps test the parity of AnitomyK and [anitomy](https://github.com/erengy/anitomy/)

Both datasets originated from anitomy. Any test units that fails in both anitomy and AnitomyK are put in [failing_data.json](failing_data.json).
These test units are expected to fail and thus will not be fixed. If one of the failing unit tests passes the test, then that is considered an issue.

Other than that, the test units that succeed in anitomy are expected to succeed in AnitomyK. Thus, these test units are put in [data.json](data.json)
