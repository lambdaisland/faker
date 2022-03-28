# faker

<!-- badges -->
[![cljdoc badge](https://cljdoc.org/badge/com.lambdaisland/faker)](https://cljdoc.org/d/com.lambdaisland/faker) [![Clojars Project](https://img.shields.io/clojars/v/com.lambdaisland/faker.svg)](https://clojars.org/com.lambdaisland/faker)
<!-- /badges -->

Port of the Faker Ruby gem

## Features

<!-- installation -->
## Installation

To use the latest release, add the following to your `deps.edn` ([Clojure CLI](https://clojure.org/guides/deps_and_cli))

```
com.lambdaisland/faker {:mvn/version "0.1.4"}
```

or add the following to your `project.clj` ([Leiningen](https://leiningen.org/))

```
[com.lambdaisland/faker "0.1.4"]
```
<!-- /installation -->

## Rationale

Create fake values that look "nice", i.e. real-world-ish, so it's quick and easy
to populate UIs to get a feel for how they behave.

The single `fake` function is the main interface, it can do a bunch of
things. You can pass it a "path" of something it knows about,

- `(fake [:address :city])`
- `(fake [:vehicle :vin])`

A partial path will return a map of all nested fakers,

- `(fake [:address])` `;;=> {:country "..", :street_address "...", ....}`

A regex will generate values that match the regex

- `(fake #"[A-Z0-9]{3}")`

A string is treated as a pattern, use #{} to nest faker paths, using dotted
notation, or "#" to put a digit.

- `(fake "#{company.name} GmbH")`
- `(fake "##-####-##")`

Use a set to pick one of multiple choices

- `(fake #{[:name :first-name] [:name :last-name]})`

A map will generate values for each map value. Fakers that are invoked
multiple times are memoized. Since a lot of fakers are based on other fakers,
this allows you to generate related values.

```
(fake {:name [:name :name] :email [:internet :email]})
;; => {:name "Freiherrin Marlena Daimer", :email "marlenadaimer@daimerohg.com"}
```

Not all fakers from the Ruby gem work, we mainly support the ones where a value
is picked from a list, or a template is populated with values from other fakers.
Some fakers rely on custom logic which often hasn't been ported. See the file
[[supported_fakers.clj]] for all fakers that currently return a value, and
[[unsupported_fakers.clj]] for the ones that return `nil` or throw an exception.
Note that "supported" is defined loosely, e.g. the credit card faker currently
returns the pattern string rather than a valid number.
  
  
<!-- opencollective -->
## Lambda Island Open Source

<img align="left" src="https://github.com/lambdaisland/open-source/raw/master/artwork/lighthouse_readme.png">

&nbsp;

faker is part of a growing collection of quality Clojure libraries created and maintained
by the fine folks at [Gaiwan](https://gaiwan.co).

Pay it forward by [becoming a backer on our Open Collective](http://opencollective.com/lambda-island),
so that we may continue to enjoy a thriving Clojure ecosystem.

You can find an overview of our projects at [lambdaisland/open-source](https://github.com/lambdaisland/open-source).

&nbsp;

&nbsp;
<!-- /opencollective -->

<!-- contributing -->
## Contributing

Everyone has a right to submit patches to faker, and thus become a contributor.

Contributors MUST

- adhere to the [LambdaIsland Clojure Style Guide](https://nextjournal.com/lambdaisland/clojure-style-guide)
- write patches that solve a problem. Start by stating the problem, then supply a minimal solution. `*`
- agree to license their contributions as MPL 2.0.
- not break the contract with downstream consumers. `**`
- not break the tests.

Contributors SHOULD

- update the CHANGELOG and README.
- add tests for new functionality.

If you submit a pull request that adheres to these rules, then it will almost
certainly be merged immediately. However some things may require more
consideration. If you add new dependencies, or significantly increase the API
surface, then we need to decide if these changes are in line with the project's
goals. In this case you can start by [writing a pitch](https://nextjournal.com/lambdaisland/pitch-template),
and collecting feedback on it.

`*` This goes for features too, a feature needs to solve a problem. State the problem it solves, then supply a minimal solution.

`**` As long as this project has not seen a public release (i.e. is not on Clojars)
we may still consider making breaking changes, if there is consensus that the
changes are justified.
<!-- /contributing -->

<!-- license -->
## License

Copyright &copy; 2022 Arne Brasseur and Contributors

Licensed under the term of the Mozilla Public License 2.0, see LICENSE.
<!-- /license -->
