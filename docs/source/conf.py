import os

project = 'TextVariantExplorer'
copyright = '2025, Paulanerus'
author = 'Paulanerus'
release = '1.2.3'

# -- General configuration ---------------------------------------------------

extensions = ['myst_parser']

templates_path = ['_templates']
exclude_patterns = []

html_baseurl = os.environ.get("READTHEDOCS_CANONICAL_URL", "/")

# -- Options for HTML output -------------------------------------------------

html_theme = 'sphinx_rtd_theme'
html_static_path = ['_static']
