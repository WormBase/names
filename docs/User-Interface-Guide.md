# Gene

All genes in the system are guarenteed to have two attributes:
 * A primary identifier (typically, a "WBGene" ID)
 * Species

## Creating and editing
The system tries to ensure that the names service records genes in the correct forms,
and to the extent reasonable, ensure validity of naming across all entities.

### Forms
Genes must be either of a _cloned_ or _uncloned_ form.
Specifying a combination of values that do not match one of these forms will result in a validation error.

### Name validation rules
CGC and Sequence names must adhere to the WormBase nomlecture guide.
If a name does not match a predefined regular expression for the species.

A cloned gene can have the following fields populated:
 * Sequence name (required)
 * Biotype  (required)
 * CGC name (optional)

An uncloned gene may have the following fields populated:
 * CGC name

Once a gene has been created, the names of the Gene cannot be removed (or set to empty).

## Changing the status of gene
There are distinct operations for changing the gene status.
 * Kill (-> "dead" status)
 * Supressed (-> "suppresssed" status)
 * Ressurect (-> "live" status)

**_Dead genes cannot be suppressed, or otherwise interacted with._**

The only action available for a dead gene is to ressurect it, at which point further actions can be taken.



